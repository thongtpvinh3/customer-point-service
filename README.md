# Customer Point Service

## 1. Yêu cầu của bài toán

* Thiết kế một service quản lý điểm thưởng của khách hàng tại một công ty kinh doanh nào đó
* Mục tiêu 1: Phân loại các tag sự kiện được công điểm hoặc trừ điểm
* Mục tiêu 2: Nhận các sự kiện từ các nguồn của kênh kinh doanh, .... để tiến hành cộng trừ điểm
* Mục tiêu 3: Nhận lệnh chuyển điểm từ khách hàng này sang khách hàng khác

## 2. Thiết kế đề xuất

Client/Another Service ---event point----->  CustomerPointService ----Tính toán điểm ---> PostgreSQL
                                                                                              |
                                                                                              |
                                                                                              |
                                                                                              |
                                             Đồng bộ vào các kết quả vào Cache <--------------

## 3. Thiết kế cấu trúc code theo từng mục tiêu

* Mục tiêu 1: Thiết kế các Enum, hoặc bảng config

  * Những sự kiện sẽ nhận các event thay đổi điểm thưởng:
    * Tạo tài khoản
    * Giao dịch ở trong tài khoản thẻ tín dụng
    * Giao dịch lớn hơn 100k trong tài khoản bất kì
    
* Mục tiêu 2: Thiết kế code theo kiểu Strategy Pattern để phù hợp với từng thuật toán của từng sự kiện
  * Thiết kế Strategy chung gọi là modifyPoint
  * Strategy cho sự kiện tạo tài khoản
  * Strategy cho sự kiện giao dịch ở trong thẻ tín dụng
  * Strategy cho sự kiện giao dịch lớn hơn 100k trong tài khoản bất kì
  
* Mục tiêu 3: Khả năng sẽ là 1 Strategy đặc biệt để chuyển điểm từ Khách hàng A sang Khách hàng B
  * Tạo 1 strategy đặc biệt cho sự kiện Khách hàng A chuyển điểm sang khách hàng B


* Chi tiết thiết kế code:

  ```
    Service nhận event -> Phân giải Event ra ModifyPointEventRecord.class 
    --> Lấy các thông tin cần thiết bao gồm:
        * userId : ID của người dùng trong hệ thống
        * modifyPointEventType : Loại sự kiện thay đổi điểm (Giúp xác định xem nên tính toán điểm như thế nào)
        * eventData : metadata của event (có thể define ra riêng các DTO Metadata riêng của từng loại sự kiện)
    --> Từ sự kiện mà Kafka nhận được, tiến hành sử dụng Strategy Pattern để xem sẽ xử lý điểm cho user như thế nào
    --> Đến ModifyPointStrategy --> tìm các configs còn hoạt động --> Lấy ra config của Strategy
    --> PointEventConfigEntity.class --> Lấy ra các thông tin bao gồm: amount
    --> Tiến hành tính toán thay đổi số điểm của người dùng userId cẩn thận đảm bảo các không gặp các lỗi sau:
        * Giao dịch lặp (Sử dụng Idempotency Key hoặc transaction id duy nhất)
        * Chuyển điểm phải đảm bảo tính hợp lệ của dữ liệu
        * Các giao dịch phải được thực hiện đồng thời
        * DeadLock nếu A và B chuyển đồng thời cho C
  ```
* `Pessimistic Locking` : Cơ chế này hoạt động bằng cách lock 1 row trước khi attr của nó được thay đổi thông qua việc gọi method setAttr()
  * Nếu có transaction nào khác cố gắng truy cập vào row đã bị khóa đó, chúng sẽ buộc phải chờ cho đến khi transaction đầu tiên hoàn thành
  * Sử dụng syntax SELECT ... FOR UPDATE
  * 2 Transaction không bao giờ có thể cùng thay đổi 1 row
  * về cơ bản toàn bộ quá trình update diễn ra như sau:
    ```
      Acquire lock.
      Update data.
      Release lock.
    ```

  * Nhược điểm:
    * Nếu 1 user chọn edit 1 record, sau đó, anh ra đi mà chưa kết thúc hay hủy bỏ transaction này --> Tất cả các user cần update record này sẽ phải
    buộc đợi cho đến khi row này được mở Locking
    * Có thể xảy ra DeadLock. Khi User A và B cùng update vào Database trong cùng một thời gian, User A sẽ khóa lại record đó và cố gắng mở khóa được
    được hình thành bởi User B cũng đang đợi mở khóa và User A vừa hình thành
  
* `Optimistic Locking`: Chỉ lock khi commit việc update.


## 4. Những lỗi sẽ có thể mắc phải khi lượng giao dịch lớn


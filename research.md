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



Có một service (customer-point-service) nhận nhiệm vụ lưu trữ điểm của người dùng từ các nguồn của một công ty A
- Yêu cầu: Thiết kế chức năng chuyển điểm số từ user A sang user B
- Thiết kế phải đảm bảo yêu cầu sau: Đảm bảo tính nhất quán của các transaction, đảm bảo việc có thể mở rộng

Từ yêu cầu trên, em thiết kế luồng đi của dữ liệu như sau:

1. Ở Client App: User A tiến hành gửi request chuyển N điểm cho User B bằng cách call API /transfer.
2. Ở API Gateway: Nhận Request và router đến các Service của các hệ thống phục vụ người dùng (Ví dụ như service backend của App)
3. Ở Service BackEnd của App: Nhận request từ người dùng, bổ sung thêm các thông tin như source, timestamp, metadata, ... và đóng gói thành ModifyPointEventRecord, gửi đến Customer-point-service theo 2 cách:
    * Sử dụng REST api: /transfer với Request Body là ModifyPointEventRecord
    * Sử dụng Kafka gửi vào topic "com.thong1.modify-point-topic" với message là ModifyPointEventRecord
4. Ở Customer-Point-Service: Nhận Request hoặc Message từ Service Backend, và tiến hành chuyển điểm theo flow sau:
    * Validate xem request hay message đã hợp lệ và đầy đủ thông tin hay chưa
    * Kiểm tra xem transactionId đã được lưu lại hay chưa, nếu transactionId tồn tại, thì không xử lý request (Tránh client gặp lỗi, gọi nhiều request giống nhau)
    * Tạo khóa redis cho user A và user B và lưu lại trên Redis với TTL = 5s (Mục đích khóa hai user này lại không cho những transaction khác được thao tác)
    * Tiến hành nghiệp vụ cộng trừ điểm cho cả 2 user A và B và lưu lại trong Database
    * Lưu lại log transaction
    * Xóa khóa trên Redis (Mở khóa cho các transaction khác được quyền thao tác trên 2 user này)
    * Trả về kết quả cho User nếu sử dụng API và có thể bắn noti cho người dùng nếu sử dụng Kafka
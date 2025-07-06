package thong.test.customerpointservice.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateUserPointRequest {

    private Long userId;

    private Long currentPoint;
}

package thong.test.customerpointservice.pojo.dto;

import lombok.Data;

@Data
public class CreateNewUserMetaData implements ModifyPointMetaData {

    private String username;
}

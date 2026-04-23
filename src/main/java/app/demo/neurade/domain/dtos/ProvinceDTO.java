package app.demo.neurade.domain.dtos;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceDTO {
    private Integer id;
    private String name;
    private String code;
}

package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/order")
@RestController
public class OrderController {

    @Autowired
    OrderController() {}

    @GetMapping("/hello")
    public ResponseEntity<String> getHomePage()
    {
        String dtoResponse = ExampleDto.getOrderDto();
        return ResponseEntity.ok(dtoResponse);
    }
}

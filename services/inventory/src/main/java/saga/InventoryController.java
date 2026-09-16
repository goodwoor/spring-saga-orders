package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/inventory")
@RestController
public class InventoryController {

    @Autowired
    InventoryController() {}

    @GetMapping("/hello")
    public ResponseEntity<String> getHomePage()
    {
        String dtoResponse = ExampleDto.getInventoryDto();
        return ResponseEntity.ok(dtoResponse);
    }
}

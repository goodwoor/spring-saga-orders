package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import saga.dto.ItemResponse;

import java.util.List;

@RequestMapping("/inventory")
@RestController
public class InventoryController {
    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<ItemResponse>> getAllItems()
    {
        List<ItemResponse> response = inventoryService.findAllItems();
        return ResponseEntity.ok(response);
    }
}

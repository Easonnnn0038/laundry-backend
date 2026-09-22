package com.laundry.api.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShelfServiceTest {
    @Test void prefersAdjacentThenContiguousSpace(){
        assertEquals(13,ShelfService.chooseShelf(20,Set.of(1,2,10),List.of(11,12),2,0));
        assertEquals(3,ShelfService.chooseShelf(8,Set.of(1,2,5),List.of(),2,0));
    }
}

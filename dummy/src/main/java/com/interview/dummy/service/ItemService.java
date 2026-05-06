package com.interview.dummy.service;

import com.interview.dummy.model.Item;
import com.interview.dummy.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository repository;

    public List<Item> findAll() {
        return repository.findAll();
    }

    public Item findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
    }

    public Item create(Item item) {
        item.setId(null);
        return repository.save(item);
    }

    public Item update(Long id, Item update) {
        Item existing = findById(id);
        existing.setName(update.getName());
        existing.setPrice(update.getPrice());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}

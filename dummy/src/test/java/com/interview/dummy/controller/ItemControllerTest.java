package com.interview.dummy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.dummy.model.Item;
import com.interview.dummy.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ItemRepository repository;

    @BeforeEach
    void cleanDb() {
        repository.deleteAll();
    }

    @Test
    void postAndGet_roundTrip() throws Exception {
        Item input = Item.builder().name("Notebook").price(4.99).build();

        String json = mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Notebook")))
                .andReturn().getResponse().getContentAsString();

        Long createdId = objectMapper.readTree(json).get("id").asLong();

        mockMvc.perform(get("/api/items/" + createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price", is(4.99)));
    }

    @Test
    void post_withInvalidPayload_returns400() throws Exception {
        String invalid = "{\"name\":\"\",\"price\":-1}";

        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());
    }
}

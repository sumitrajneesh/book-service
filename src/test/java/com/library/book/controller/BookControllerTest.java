package com.library.book.controller;

import com.library.book.model.Book;
import com.library.book.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
public class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookRepository bookRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Book book1;
    private Book book2;

    @BeforeEach
    void setUp() {
        book1 = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 3);
        book2 = new Book(2L, "1984", "George Orwell", "978-0451524935", 3, 3);
    }

    @Test
    void testGetAllBooks() throws Exception {
        when(bookRepository.findAll()).thenReturn(Arrays.asList(book1, book2));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].title").value("The Great Gatsby"))
                .andExpect(jsonPath("$[1].author").value("George Orwell"));
    }

    @Test
    void testGetBookByIdFound() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book1));

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("The Great Gatsby"));
    }

    @Test
    void testGetBookByIdNotFound() throws Exception {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateBook() throws Exception {
        Book newBook = new Book("To Kill a Mockingbird", "Harper Lee", "978-0446310789", 10);
        Book savedBook = new Book(3L, "To Kill a Mockingbird", "Harper Lee", "978-0446310789", 10, 10);
        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newBook)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.title").value("To Kill a Mockingbird"))
                .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    @Test
    void testUpdateBook() throws Exception {
        Book updatedDetails = new Book(null, null, null, 7); // Only changing quantity
        Book existingBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 3);
        Book savedBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 7, 5); // 3 + (7-5) = 5

        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        mockMvc.perform(put("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.availableQuantity").value(5));
    }

    @Test
    void testDeleteBook() throws Exception {
        doNothing().when(bookRepository).deleteById(1L);

        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testUpdateAvailableQuantityIncrement() throws Exception {
        Book existingBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 3);
        Book updatedBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 4);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBook);

        mockMvc.perform(put("/api/books/1/available?change=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(4));
    }

    @Test
    void testUpdateAvailableQuantityDecrement() throws Exception {
        Book existingBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 3);
        Book updatedBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 2);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBook);

        mockMvc.perform(put("/api/books/1/available?change=-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(2));
    }

    @Test
    void testUpdateAvailableQuantityInvalidChange() throws Exception {
        Book existingBook = new Book(1L, "The Great Gatsby", "F. Scott Fitzgerald", "978-0743273565", 5, 0); // 0 available
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existingBook));

        mockMvc.perform(put("/api/books/1/available?change=-1")) // Try to go below 0
                .andExpect(status().isBadRequest());
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/books/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}
package com.library.book.controller;

import com.library.book.model.Book;
import com.library.book.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "*") // Allow all origins for simplicity in demo, restrict in production
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @GetMapping
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        Optional<Book> book = bookRepository.findById(id);
        return book.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody Book book) {
        book.setId(null); // Ensure ID is null for new book creation
        book.setAvailableQuantity(book.getQuantity()); // Set available quantity initially
        Book savedBook = bookRepository.save(book);
        return new ResponseEntity<>(savedBook, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @RequestBody Book bookDetails) {
        Optional<Book> book = bookRepository.findById(id);
        if (book.isPresent()) {
            Book existingBook = book.get();
            existingBook.setTitle(bookDetails.getTitle());
            existingBook.setAuthor(bookDetails.getAuthor());
            existingBook.setIsbn(bookDetails.getIsbn());
            // Logic to handle quantity changes:
            // If total quantity increases, available quantity increases by the difference
            // If total quantity decreases, ensure available quantity doesn't exceed new total
            int oldQuantity = existingBook.getQuantity();
            int newQuantity = bookDetails.getQuantity();
            int quantityDiff = newQuantity - oldQuantity;

            existingBook.setQuantity(newQuantity);
            existingBook.setAvailableQuantity(existingBook.getAvailableQuantity() + quantityDiff);
            if (existingBook.getAvailableQuantity() < 0) { // Should not happen with proper loan management
                existingBook.setAvailableQuantity(0);
            }
            if (existingBook.getAvailableQuantity() > existingBook.getQuantity()) {
                existingBook.setAvailableQuantity(existingBook.getQuantity());
            }

            Book updatedBook = bookRepository.save(existingBook);
            return ResponseEntity.ok(updatedBook);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteBook(@PathVariable Long id) {
        try {
            bookRepository.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Endpoint to update available quantity (called by Loan Service)
    @PutMapping("/{id}/available")
    public ResponseEntity<Book> updateAvailableQuantity(@PathVariable Long id, @RequestParam int change) {
        Optional<Book> bookOptional = bookRepository.findById(id);
        if (bookOptional.isPresent()) {
            Book book = bookOptional.get();
            int newAvailable = book.getAvailableQuantity() + change;
            if (newAvailable < 0 || newAvailable > book.getQuantity()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // Cannot go below 0 or above total quantity
            }
            book.setAvailableQuantity(newAvailable);
            Book updatedBook = bookRepository.save(book);
            return ResponseEntity.ok(updatedBook);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // Basic health check endpoint
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
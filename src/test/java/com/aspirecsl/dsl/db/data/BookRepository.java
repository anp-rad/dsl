package com.aspirecsl.dsl.db.data;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.aspirecsl.dsl.db.BaseRepository;
import com.aspirecsl.dsl.db.data.Book;

@Repository
@Transactional
public interface BookRepository extends BaseRepository<Book> {
}

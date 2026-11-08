package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.Image;
import com.example.restcontrollerdemo.dto.PostCount;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ImageRepository extends ListCrudRepository<Image, Long> {

    /** hasMany (post → images), at most 10 rows (CHECK on position). */
    List<Image> findAllByPostIdOrderByPositionAsc(Long postId);

    /** One aggregate for a whole page of posts, instead of one COUNT per post. */
    @Query("SELECT post_id, COUNT(*) AS total FROM images WHERE post_id IN (:postIds) GROUP BY post_id")
    List<PostCount> countByPostIds(Collection<Long> postIds);
}

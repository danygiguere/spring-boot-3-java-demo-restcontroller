package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.validation.NoDuplicates;
import com.example.restcontrollerdemo.validation.SafeText;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.URL;

/** The author is the authenticated user, never a request field. */
@Schema(description = "New post, with up to 10 images in display order")
public record CreatePostRequest(
        @NotBlank(message = "{post.content.required}")
                @Size(max = 5000, message = "{post.content.size}")
                @SafeText(message = "{post.content.unsafe}")
                @Schema(example = "Hello from the router demo!")
                String content,
        @Size(max = 10, message = "{post.imageUrls.size}")
                @NoDuplicates(message = "{post.imageUrls.duplicate}")
                @ArraySchema(schema = @Schema(example = "https://images.example.com/hello.jpg"))
                List<
                                @NotBlank(message = "{post.imageUrl.required}")
                                @URL(protocol = "https", message = "{post.imageUrl.url}")
                                @Size(max = 500, message = "{post.imageUrl.size}") String>
                        imageUrls) {

    public CreatePostRequest {
        imageUrls = imageUrls == null ? List.of() : imageUrls;
    }
}

package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.dto.ImageResponse;
import com.example.restcontrollerdemo.dto.MeResponse;
import com.example.restcontrollerdemo.dto.ProfileRequest;
import com.example.restcontrollerdemo.dto.ProfileResponse;
import com.example.restcontrollerdemo.dto.UserResponse;
import com.example.restcontrollerdemo.exception.AppException;
import com.example.restcontrollerdemo.repository.ImageRepository;
import com.example.restcontrollerdemo.repository.ProfileRepository;
import com.example.restcontrollerdemo.repository.UserRepository;
import com.example.restcontrollerdemo.security.AuthenticatedUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ImageRepository imageRepository;

    public UserService(
            UserRepository userRepository, ProfileRepository profileRepository, ImageRepository imageRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.imageRepository = imageRepository;
    }

    public MeResponse me(AuthenticatedUser me) {
        return MeResponse.of(requireUser(me.userId()));
    }

    public List<UserResponse> findAll(long afterId, int limit) {
        return userRepository.findPage(afterId, limit).stream()
                .map(UserResponse::of)
                .toList();
    }

    public UserResponse findById(long id) {
        return UserResponse.of(requireUser(id));
    }

    public ProfileResponse findProfile(long userId) {
        User user = requireUser(userId);
        return profileRepository
                .findByUserId(user.id())
                .map(ProfileResponse::of)
                .orElseThrow(() -> new AppException.NotFound("error.profile.not.found", userId));
    }

    /** Only the caller's own profile is writable; the target comes from the token, not the request. */
    public ProfileResponse replaceMyProfile(AuthenticatedUser me, ProfileRequest request) {
        User user = requireUser(me.userId());
        return ProfileResponse.of(profileRepository.upsert(user.id(), request.bio(), request.avatarUrl()));
    }

    public List<ImageResponse> findImages(long userId, long beforeId, int limit) {
        requireUser(userId);
        return imageRepository.findByUser(userId, beforeId, limit).stream()
                .map(ImageResponse::of)
                .toList();
    }

    public User requireUser(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new AppException.NotFound("error.user.not.found", userId));
    }
}

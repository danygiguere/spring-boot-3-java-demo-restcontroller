package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.dto.MeResponse;
import com.example.restcontrollerdemo.dto.ProfileRequest;
import com.example.restcontrollerdemo.dto.ProfileResponse;
import com.example.restcontrollerdemo.dto.UserResponse;
import com.example.restcontrollerdemo.exception.AppException;
import com.example.restcontrollerdemo.repository.ProfileRepository;
import com.example.restcontrollerdemo.repository.UserRepository;
import com.example.restcontrollerdemo.security.AuthenticatedUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    public MeResponse me(AuthenticatedUser me) {
        return MeResponse.of(requireColleague(me, me.userId()));
    }

    public List<UserResponse> findAll(AuthenticatedUser me, long afterId, int limit) {
        return userRepository.findPageInEnterprise(me.enterpriseId(), afterId, limit).stream()
                .map(UserResponse::of)
                .toList();
    }

    public UserResponse findById(AuthenticatedUser me, long id) {
        return UserResponse.of(requireColleague(me, id));
    }

    public ProfileResponse findProfile(AuthenticatedUser me, long userId) {
        User user = requireColleague(me, userId);
        return profileRepository
                .findByUserId(user.id())
                .map(ProfileResponse::of)
                .orElseThrow(() -> new AppException.NotFound("error.profile.not.found", userId));
    }

    /** Only the caller's own profile is writable; the target comes from the token, not the request. */
    public ProfileResponse replaceMyProfile(AuthenticatedUser me, ProfileRequest request) {
        User user = requireColleague(me, me.userId());
        return ProfileResponse.of(profileRepository.upsert(user.id(), request.bio(), request.avatarUrl()));
    }

    /** A user of the caller's enterprise; any other user is reported as not found (no cross-tenant probing). */
    public User requireColleague(AuthenticatedUser me, long userId) {
        return userRepository
                .findInEnterprise(userId, me.enterpriseId())
                .orElseThrow(() -> new AppException.NotFound("error.user.not.found", userId));
    }
}

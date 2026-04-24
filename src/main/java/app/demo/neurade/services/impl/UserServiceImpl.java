package app.demo.neurade.services.impl;

import app.demo.neurade.domain.dtos.UserAndInfoDTO;
import app.demo.neurade.domain.dtos.messages.UserCreatedMessage;
import app.demo.neurade.domain.dtos.messages.MessageStatus;
import app.demo.neurade.domain.dtos.requests.PatchUserRequest;
import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.domain.mappers.UserInformationMapper;
import app.demo.neurade.domain.models.RoleType;
import app.demo.neurade.domain.models.User;
import app.demo.neurade.domain.models.UserInformation;
import app.demo.neurade.domain.models.Role;
import app.demo.neurade.domain.models.Province;
import app.demo.neurade.domain.models.Commune;
import app.demo.neurade.infrastructures.repositories.PeopleManagementRepository;
import app.demo.neurade.infrastructures.repositories.UserInformationRepository;
import app.demo.neurade.infrastructures.repositories.UserRepository;
import app.demo.neurade.infrastructures.repositories.RoleRepository;
import app.demo.neurade.security.RegisterRequest;
import app.demo.neurade.services.UserService;
import app.demo.neurade.services.UserPersistenceService;
import app.demo.neurade.services.UserAndInfoParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserInformationRepository infoRepository;
    private final PeopleManagementRepository peopleManagementRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final Mapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserInformationMapper userInformationMapper;
    private final UserPersistenceService userPersistenceService;

    @Override
    @Transactional
    public boolean updateUserInfo(User user, PatchUserRequest req) {
        UserInformation info = infoRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("User information not found"));

        userInformationMapper.patchUserInfo(req, info);
        infoRepository.save(info);

        return true;
    }

    @Override
    public List<User> getUsersUnderManagement(User user) {
        List<User> users = new ArrayList<>();
        if (user.getRole().isRoleType(RoleType.ADMIN)) {
            users = userRepository.findAll();
            users.remove(user);
        } else if (
                user.getRole().isRoleType(RoleType.ORGANIZATION) ||
            user.getRole().isRoleType(RoleType.TEACHER)
        ) {
            users = peopleManagementRepository.findManagedUsersByManagerId(user.getId());
        }
        return users;
    }

    @Override
    public UserAndInfoDTO getUserAndInfo(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        UserInformation info = infoRepository.findByUser_Id(id)
                .orElseThrow(() -> new EntityNotFoundException("User information not found for user id: " + id));
        return mapper.toDto(user, info);
    }

    @Override
    public List<UserAndInfoDTO> getAllUsersAndInfo() {
        List<UserInformation> infos = infoRepository.findInfoByAllUser();
        return infos.stream()
                .map(info -> {
                    User user = info.getUser();
                    return mapper.toDto(user, info);
                })
                .toList();
    }

    @Override
    @Transactional
    public User createUserWithInformation(RegisterRequest req) {
        // Check if user already exists
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            log.warn("{} is already registered", req.getEmail());
            throw new IllegalArgumentException("User already exists with email: " + req.getEmail());
        }

        Role role = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> {
                    log.warn("Role with id {} not found", req.getRoleId());
                    return new IllegalArgumentException("Role not found with id: " + req.getRoleId());
                });

        Province city = entityManager.getReference(Province.class, req.getCityCode());
        Commune commune = entityManager.getReference(Commune.class, req.getSubDistrictCode());

        UserAndInfoParams params = UserAndInfoParams.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .city(city)
                .commune(commune)
                .bio(req.getBio())
                .school(req.getSchool())
                .grade(req.getGrade())
                .addressDetail(req.getAddressDetail())
                .dateOfBirth(req.getDateOfBirth())
                .favoriteSubjects(req.getFavoriteSubjects())
                .build();

        User user = userPersistenceService.createUserAndInfo(params);
        log.info("User {} created successfully with full information", user.getEmail());

        return user;
    }

    @Override
    @Transactional
    public User createUserFromOAuth(String email, OAuth2User oauth2User) {
        // Check if user already exists
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("User {} already exists", email);
            return userRepository.findByEmail(email).get();
        }

        Role roleRef = entityManager.getReference(Role.class, RoleType.STUDENT.getRoleId());

        UserAndInfoParams params = UserAndInfoParams.builder()
                .email(email)
                .password("")
                .role(roleRef)
                .firstName(oauth2User.getAttribute("given_name"))
                .lastName(oauth2User.getAttribute("family_name"))
                .avatarImage(oauth2User.getAttribute("picture"))
                .verified(oauth2User.getAttribute("email_verified"))
                .build();

        User user = userPersistenceService.createUserAndInfo(params);

        log.info("User {} registered successfully via OAuth", email);

        publishUserCreatedMessage(user, UserCreatedMessage.UserCreationSource.OAUTH);
        return user;
    }

    private void publishUserCreatedMessage(User user, UserCreatedMessage.UserCreationSource source) {
        try {
            UserCreatedMessage message = UserCreatedMessage.builder()
                    .id(UUID.randomUUID())
                    .status(MessageStatus.QUEUED)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .roleId(user.getRole().getId())
                    .source(source)
                    .build();

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(message);
                        log.info("Queued user created message for user: {} (after commit)", user.getEmail());
                    }
                });
            } else {
                eventPublisher.publishEvent(message);
                log.info("Queued user created message for user: {} (no transaction)", user.getEmail());
            }
        } catch (Exception e) {
            log.error("Failed to publish user created message for user: {}", user.getEmail(), e);
        }
    }
}

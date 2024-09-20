package org.evpro.bookshopV5.service;


import org.evpro.bookshopV5.exception.BookException;
import org.evpro.bookshopV5.model.*;
import org.evpro.bookshopV5.model.DTO.request.AddUserRequest;
import org.evpro.bookshopV5.model.DTO.request.UpdateRoleRequest;
import org.evpro.bookshopV5.model.DTO.response.*;
import org.evpro.bookshopV5.exception.UserException;
import org.evpro.bookshopV5.model.enums.ErrorCode;
import org.evpro.bookshopV5.model.enums.RoleCode;
import org.evpro.bookshopV5.repository.CartRepository;
import org.evpro.bookshopV5.repository.RoleRepository;
import org.evpro.bookshopV5.repository.UserRepository;
import org.evpro.bookshopV5.service.functions.UserFunctions;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class UserService implements UserFunctions {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CartRepository cartRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;

    }

    @Override
    public UserDTO getUserById(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(
                        new ErrorResponse(
                                ErrorCode.EUN,
                                "User not found with id " + userId)));
        return convertToUserDTO(user);
    }

    @Override
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserException(
                        new ErrorResponse(
                                ErrorCode.EUN,
                                "User not found with email " + email )));
        return convertToUserDTO(user);
    }

    @Override
    public UserDTO updateUserProfile(Integer userId, String newName, String newSurname) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(
                        new ErrorResponse(
                                ErrorCode.EUN,
                                "User not found with id " + userId)));
        existingUser.setName(newName);
        existingUser.setSurname(newSurname);
        userRepository.save(existingUser);
        return convertToUserDTO(existingUser);
    }

    @Transactional
    @Override
    public boolean changeEmail(Integer userId, String password, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UserException(new ErrorResponse(ErrorCode.IVP, "Invalid password"));
        }

        if (userRepository.findByEmail(newEmail).isPresent()) {
            throw new UserException(new ErrorResponse(ErrorCode.EAE, "Email already in use"));
        }

        user.setEmail(newEmail);
        userRepository.save(user);
        return true;
    }

    @Transactional
    @Override
    public boolean changeUserPassword(Integer userId, String oldPassword, String newPassword, String confirmNewPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new UserException(new ErrorResponse(ErrorCode.IVP, "Invalid old password"));
        }

        if (!newPassword.equals(confirmNewPassword)) {
            throw new UserException(new ErrorResponse(ErrorCode.PWM, "New passwords do not match"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }

    @Override
    public List<LoanDTO> getUserLoanHistory(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        return user.getLoans().stream()
                .map(this::convertToLoanDTO)
                .collect(Collectors.toList());
    }


    @Override
    public CartDTO getUserCart(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        return convertToCartDTO(user.getCart());
    }

    @Transactional
    @Override
    public UserDTO addNewUser(AddUserRequest request) {
        if (request == null) {
            throw new UserException(
                    new ErrorResponse(
                            ErrorCode.NCU,
                            "No user provided to add"));
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserException(new ErrorResponse(ErrorCode.EAE, "User with this email already exists"));
        }

        User newUser = initializeUserFromRequest(request);
        userRepository.save(newUser);
        return convertToUserDTO(newUser);
    }

    @Transactional
    @Override
    public List<UserDTO> addNewUsers(List<AddUserRequest> requests) {
        if (requests.isEmpty()) {
            throw new UserException(
                    new ErrorResponse(
                            ErrorCode.NCU,
                            "No user provided to add"));
        }
        List<UserDTO> addedUser = new ArrayList<>();
        for (AddUserRequest request: requests) {
            Optional<User> existingUserOptional = userRepository.findByEmail(request.getEmail());
            if(existingUserOptional.isPresent()) {
                throw  new UserException(
                        new ErrorResponse(
                                ErrorCode.EAE,
                                "User with this mail already exists "));
            }
            User newUser = initializeUserFromRequest(request);
            userRepository.save(newUser);
            UserDTO userDTO = convertToUserDTO(newUser);
            addedUser.add(userDTO);
        }
        return addedUser;
    }

    @Override
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        if(users.isEmpty()) {
            throw new UserException(
                  new ErrorResponse(
                            ErrorCode.NCU,
                            "No user found"));
        }
        return users.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public UserDTO updateUserRole(Integer userId, UpdateRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        user.getRoles().clear();

        for (RoleCode roleCode : request.getRoleCodes()) {
            Role role = roleRepository.findByRoleCode(roleCode)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleCode));
            user.getRoles().add(role);
        }

        User updatedUser = userRepository.save(user);
        return convertToUserDTO(updatedUser);
    }

    @Transactional
    @Override
    public boolean deactivateUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        user.setActive(false);
        userRepository.save(user);
        return true;
    }

    @Transactional
    @Override
    public boolean reactivateUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        user.setActive(true);
        userRepository.save(user);
        return true;
    }

    @Override
    public long getTotalUserCount() {
        List<User> users = userRepository.findAll();
        if(users.isEmpty()) {
            throw new UserException(
                    new ErrorResponse(
                            ErrorCode.NCU,
                            "No user found"));
        }
        return users.size();
    }

    @Override
    public List<UserDTO> getMostActiveUsers(int limit) {
        List<User> activeUsers = userRepository.findMostActiveUsers(PageRequest.of(0, limit));
        return activeUsers.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserDTO> getUsersWithOverdueLoans() {

        List<User> usersWithOverdueLoans = userRepository.findUsersWithOverdueLoans();
        return usersWithOverdueLoans.stream()
                .map(this::convertToUserDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public boolean deleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(
                        new ErrorResponse(
                                ErrorCode.EUN,
                                "User not found with id " + userId)));
        userRepository.delete(user);
        return true;
    }

    @Transactional
    @Override
    public boolean deleteAll() {
        List<User> users = userRepository.findAll();
        if(users.isEmpty()) {
            throw new UserException(
                    new ErrorResponse(
                            ErrorCode.NCU,
                            "No user found"));
        }
        userRepository.deleteAll();
        return true;
    }

    @Override
    public String resetUserPassword(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(
                        new ErrorResponse(ErrorCode.EUN, "User not found with id " + userId)));

        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return newPassword;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    private User initializeUserFromRequest(AddUserRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setSurname(request.getSurname());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setActive(request.isActive());

        List<Role> roles = new ArrayList<>();
        for (RoleCode roleCode : request.getRoleCodes()) {
            Role role = roleRepository.findByRoleCode(roleCode)
                    .orElseThrow(() -> new RuntimeException("Role not found: " + roleCode));
            roles.add(role);
        }

        user.setRoles(roles);
        return user;
    }
    private UserDTO convertToUserDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .surname(user.getSurname())
                .email(user.getEmail())
                .roles(user.getRoles())
                .active(user.isActive())
                .build();
    }
    private LoanDTO convertToLoanDTO(Loan loan) {
        return  LoanDTO.builder()
                .id(loan.getId())
                .loanDate(loan.getLoanDate())
                .dueDate(loan.getDueDate())
                .loanDetails(loan.getLoanDetails())
                .returnDate(loan.getReturnDate())
                .user(loan.getUser())
                .status(loan.getStatus())
                .build();
    }
    private CartDTO convertToCartDTO(Cart cart) {
        CartDTO cartDTO = new CartDTO();
        cartDTO.setId(cart.getId());
        cartDTO.setCreatedDate(cart.getCreatedDate());
        cartDTO.setStatus(cart.getStatus());

        List<CartItemDTO> cartItemDTOs = cart.getItems().stream()
                .map(this::convertToCartItemDTO)
                .collect(Collectors.toList());
        cartDTO.setItems(cartItemDTOs);

        return cartDTO;
    }

    private CartItemDTO convertToCartItemDTO(CartItem cartItem) {
        CartItemDTO dto = new CartItemDTO();
        dto.setId(cartItem.getId());
        dto.setBook(cartItem.getBook());
        dto.setBook(cartItem.getBook());
        dto.setQuantity(cartItem.getQuantity());
        return dto;
    }
}
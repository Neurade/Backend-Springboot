package app.demo.neurade.security;

import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/auth")
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register user", description = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Parameter(description = "Registration payload including account and profile details")
            @RequestBody RegisterRequest request
    ) {
        UserDetails userDetails = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                userDetails = (UserDetails) principal;
            }
        }
        return ResponseEntity.ok(authService.register(userDetails, request));
    }

    @Operation(summary = "Login user", description = "Authenticate user and return tokens")
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Parameter(description = "Login payload containing credential values")
            @RequestBody AuthRequest request
    ) {
        List<String> tokens = authService.login(request);
        return ResponseEntity.ok().body(java.util.Map.of(
            "accessToken", tokens.get(0),
            "refreshToken", tokens.get(1)
        ));
    }
}

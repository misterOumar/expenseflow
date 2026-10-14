package dev.misteroumar.expenseflow.auth;

import dev.misteroumar.expenseflow.auth.dto.LoginRequest;
import dev.misteroumar.expenseflow.auth.dto.TokenResponse;
import dev.misteroumar.expenseflow.security.JwtProperties;
import dev.misteroumar.expenseflow.user.User;
import dev.misteroumar.expenseflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Hash factice comparé quand l'email est inconnu, pour que le temps de réponse ne révèle pas son existence. */
    private static final String DUMMY_HASH = "$2a$10$Nllal6dNT.RFW0UUB5nANOJw/b8MpBnJfKRkGKTImhBfVjbPZ8f6y";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /**
     * Vérifie les identifiants et émet un JWT valable {@code expenseflow.jwt.ttl}.
     *
     * @throws BadCredentialsException si l'email est inconnu ou le mot de passe incorrect,
     *                                 sans distinguer les deux cas
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(request.email());
        String hash = user.map(User::getPasswordHash).orElse(DUMMY_HASH);
        if (!passwordEncoder.matches(request.password(), hash) || user.isEmpty()) {
            throw new BadCredentialsException("Email ou mot de passe incorrect.");
        }
        return issueToken(user.get());
    }

    private TokenResponse issueToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("expenseflow")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.ttl()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", jwtProperties.ttl().toSeconds());
    }
}
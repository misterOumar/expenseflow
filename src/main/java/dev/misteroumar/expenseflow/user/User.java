package dev.misteroumar.expenseflow.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, name = "email")
    private String email;

    @Column(name = "full_name",  nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "role")
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

   @Column(nullable = false,name = "created_at")
    private Instant createdAt;

   @Column(name = "password_hash", nullable = false)
   private String passwordHash;


    public User(String email, String fullName, Role role, User manager, String passwordHash) {
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.manager = manager;
        this.passwordHash =  passwordHash;
        this.createdAt = Instant.now();
   }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}

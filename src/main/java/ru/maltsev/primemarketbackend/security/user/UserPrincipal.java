package ru.maltsev.primemarketbackend.security.user;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.maltsev.primemarketbackend.user.domain.User;

@Getter
public class UserPrincipal implements UserDetails {
    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> authorities = new LinkedHashSet<>();
        if (user.getRoles().isEmpty()) {
            authorities.add("ROLE_USER");
        } else {
            user.getRoles().forEach(role -> authorities.add("ROLE_" + role.getCode()));
        }
        user.getPermissions().forEach(permission -> authorities.add(permission.getCode()));
        return authorities.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}

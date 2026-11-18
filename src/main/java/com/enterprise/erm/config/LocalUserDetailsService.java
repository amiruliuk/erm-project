package com.enterprise.erm.config;
import com.enterprise.erm.repository.AppUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class LocalUserDetailsService implements UserDetailsService {
 private final AppUserRepository users;
 @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
  var u=users.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("User not found"));
  return User.withUsername(u.getUsername()).password(u.getPassword()).authorities(u.getRole()).disabled(!u.isEnabled()).build();
 }
}

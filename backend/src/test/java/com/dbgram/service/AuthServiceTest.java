package com.dbgram.service;
import com.dbgram.domain.user.*;
import com.dbgram.repository.*;
import com.dbgram.security.JwtTokenProvider;
import com.dbgram.util.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
 private final UserRepository users=mock(UserRepository.class);
 private final RefreshTokenRepository refresh=mock(RefreshTokenRepository.class);
 private final PasswordEncoder encoder=mock(PasswordEncoder.class);
 private final JwtTokenProvider jwt=mock(JwtTokenProvider.class);
 private final AuthService auth=new AuthService(users,refresh,encoder,jwt);
 @Test void rotatesValidRefreshAndRefusesReplay() {
  UUID id=UUID.randomUUID();var token=RefreshToken.builder().userId(id).expiresAt(Instant.now().plusSeconds(60)).build();
  when(refresh.findByTokenHash(anyString())).thenReturn(Optional.of(token));when(users.findById(id)).thenReturn(Optional.of(User.builder().id(id).username("alice").build()));when(jwt.generateAccessToken(id,"alice")).thenReturn("access");when(jwt.generateRefreshToken()).thenReturn("rotated");ReflectionTestUtils.setField(auth,"refreshTokenExpirationMs",60000L);
  assertEquals("rotated",auth.refreshAccessToken("original").refreshToken());assertTrue(token.getRevoked());assertThrows(AppException.class,()->auth.refreshAccessToken("original"));verify(jwt,times(1)).generateRefreshToken();
 }
 @Test void refreshCannotReactivateSuspendedAccount() {
  UUID id=UUID.randomUUID();when(refresh.findByTokenHash(anyString())).thenReturn(Optional.of(RefreshToken.builder().userId(id).expiresAt(Instant.now().plusSeconds(60)).build()));when(users.findById(id)).thenReturn(Optional.of(User.builder().id(id).status(User.UserStatus.SUSPENDED).build()));assertThrows(AppException.class,()->auth.refreshAccessToken("token"));verify(jwt,never()).generateRefreshToken();
 }
}

package com.tranverse.chatserver.benchmark;

import com.tranverse.chatserver.entity.User;
import com.tranverse.chatserver.enums.SystemRole;
import com.tranverse.chatserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("benchmark")
@RequiredArgsConstructor
public class ConcurrentSendBenchmarkSetupService {
    public static final String EMAIL = "concurrent-send@benchmark.local";
    public static final String PASSWORD = "BenchmarkPassword123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void initialize() {
        if (userRepository.findByEmail(EMAIL).isPresent()) {
            return;
        }
        userRepository.save(User.create(
                "Concurrent Send Benchmark",
                "concurrent_send_benchmark",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                SystemRole.USER
        ));
    }
}

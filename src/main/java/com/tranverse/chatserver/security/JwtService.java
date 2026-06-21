package com.tranverse.chatserver.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties props;
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    public String generateAccessToken(String userId, String role){
        Instant now = Instant.now();
        Instant expireAt = now.plus(props.accessExpirationMinutes(), ChronoUnit.MINUTES);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("Tran")
                .subject(userId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expireAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_ROLE, role).build();
        return sign(claims, props.accessKey());
    }

    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(
                props.refreshExpirationDays(),
                ChronoUnit.DAYS
        );

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("Tran")
                .subject(userId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .build();

        return sign(claims, props.refreshKey());
    }

    private String sign(JWTClaimsSet claimsSet, String secret){
        try {
            JWSSigner signer = new MACSigner(secret);

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claimsSet
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        }catch (JOSEException e){
            throw new IllegalStateException("Failed to sign JWT",e);
        }
    }
}

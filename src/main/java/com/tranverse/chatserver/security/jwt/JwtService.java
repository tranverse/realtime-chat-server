package com.tranverse.chatserver.security.jwt;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tranverse.chatserver.enums.ErrorCode;
import com.tranverse.chatserver.exception.AppException;
import com.tranverse.chatserver.repository.RefreshTokenRepository;
import com.tranverse.chatserver.utils.HashTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties props;
    private static final String CLAIM_AUTHORITY = "authorities";
    private static final String CLAIM_TOKEN_TYPE = "token_type";

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String ISSUER = "tranverse";

    public String generateAccessToken(String userId, String role){
        Instant now = Instant.now();
        Instant expireAt = now.plus(props.accessExpirationMinutes(), ChronoUnit.MINUTES);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expireAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_AUTHORITY, List.of(role)).build();
        return sign(claims, props.accessKey());
    }

    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(
                props.refreshExpirationDays(),
                ChronoUnit.DAYS
        );

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
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

    public SignedJWT verifyRefreshToken(String token){
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(props.refreshKey());

            if (!signedJWT.verify(verifier)) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();

            if (!ISSUER.equals(claims.getIssuer()) || expiration == null) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            if(expiration.before(new Date())){
                throw new AppException(ErrorCode.TOKEN_EXPIRED);
            }

            String tokenType = claims.getStringClaim(CLAIM_TOKEN_TYPE);

            if(!TOKEN_TYPE_REFRESH.equals(tokenType)){
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            return signedJWT;
        } catch (AppException e) {
            throw e;
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}

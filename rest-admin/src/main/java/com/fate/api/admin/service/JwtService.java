package com.fate.api.admin.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.fate.api.admin.dto.JwtToken;
import com.fate.api.admin.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class JwtService {
    @Autowired
    CacheService cacheService;

    private static final String ISSUER="wwww.wwyll.com";
    private static final String SUBJECT="atoken";
    private static final String PREFIX="atoken:";
    private static final String SECRETKEY="u92392f923frw2fdsot";
    private static final Long EXPIRE=720000L;

    /**
     * 生成token
     * @param jwtToken
     * @return
     */
    public String createToken(JwtToken jwtToken) {
        String token="";
        String key=getTokenKey(jwtToken);
        token= JWT.create().withKeyId(key)
                .withIssuer(ISSUER)
                .withSubject(SUBJECT)
                .withIssuedAt(new Date())
                .withAudience(JSON.toJSONString(jwtToken))
                .sign(Algorithm.HMAC256(SECRETKEY));
        cacheService.del(key);
        cacheService.set(key,token,EXPIRE);
        return token;
    }

    /**
     * 删除token
     * @param token
     */
    public void abolishToken(String token) {
        String key=JWT.decode(token).getKeyId();
        cacheService.del(key);
    }

    /**
     * 生产唯一的token key
     * @param jwtToken
     * @return
     */
    private String getTokenKey(JwtToken jwtToken){
        return PREFIX+jwtToken.getUserId();
    }

    /**
     * 校验JWT
     * @param token
     * @return
     */
    public Optional<JwtToken> verifierToken(String token) {
        try {
            JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(SECRETKEY)).build();
            String json=jwtVerifier.verify(token).getAudience().get(0);
            if (json!=null){
                String key=JWT.decode(token).getKeyId();
                String cacheToken= (String) cacheService.get(key);
                if (cacheToken!=null && cacheToken.equals(token)){
                    Optional<JwtToken> jwt=Optional.ofNullable(JSONObject.parseObject(json,JwtToken.class));
                    return jwt;
                }
            }
            return Optional.empty();
        }catch (Exception e){
            log.error("校验失败",e);
            throw new AuthException();
        }
    }

}

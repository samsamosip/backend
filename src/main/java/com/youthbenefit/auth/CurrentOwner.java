package com.youthbenefit.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 요청의 데이터 주인(UUID). 로그인했으면 Authorization: Bearer 토큰의 계정, 아니면 X-Anonymous-Id 헤더.
 * {@code required = false} 면 둘 다 없을 때 null.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentOwner {

	boolean required() default true;

}

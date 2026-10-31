package com.myroutine.common.security;

import java.util.UUID;

public interface TokenVersionProvider {
    int currentVersion(UUID memberId);
}

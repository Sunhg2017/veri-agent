package com.songhg.veri.agent.management.application;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.api.request.UpdateUserRequest;
import com.songhg.veri.agent.management.api.response.UserView;

public interface UserOperations {

    PageResponse<UserView> users(PageQuery pageQuery);

    UserView user(String username);

    UserView createUser(String username, AuthUserPrincipal actor);

    UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor);

    UserView enableUser(String username, AuthUserPrincipal actor);

    UserView disableUser(String username, AuthUserPrincipal actor);

    UserView lockUser(String username, AuthUserPrincipal actor);

    UserView unlockUser(String username, AuthUserPrincipal actor);

    UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor);
}

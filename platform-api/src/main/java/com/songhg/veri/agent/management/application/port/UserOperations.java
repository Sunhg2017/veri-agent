package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.auth.application.AuthUserPrincipal;
import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.api.PageResponse;
import com.songhg.veri.agent.management.application.command.UpdateUserRequest;
import com.songhg.veri.agent.management.application.view.UserView;

/**
 * User account management use cases. Lifecycle operations must protect the current session user and
 * leave audit evidence for security review.
 */
public interface UserOperations {

    /**
     * Lists users for management screens.
     */
    PageResponse<UserView> users(PageQuery pageQuery);

    /**
     * Returns one user by username.
     */
    UserView user(String username);

    /**
     * Creates a user account using repository defaults for activation and credentials.
     */
    UserView createUser(String username, AuthUserPrincipal actor);

    /**
     * Updates editable user profile fields without changing roles or lifecycle status.
     */
    UserView updateUser(String username, UpdateUserRequest request, AuthUserPrincipal actor);

    /**
     * Enables a disabled account.
     */
    UserView enableUser(String username, AuthUserPrincipal actor);

    /**
     * Disables an account while retaining its audit history and role bindings.
     */
    UserView disableUser(String username, AuthUserPrincipal actor);

    /**
     * Locks an account for security control without deleting it.
     */
    UserView lockUser(String username, AuthUserPrincipal actor);

    /**
     * Unlocks a previously locked account.
     */
    UserView unlockUser(String username, AuthUserPrincipal actor);

    /**
     * Resets the user's password through the configured password encoder.
     */
    UserView resetUserPassword(String username, String newPassword, AuthUserPrincipal actor);
}

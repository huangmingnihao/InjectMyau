package me.ksyz.accountmanager.auth;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.cookie.CookieAuth;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import org.apache.commons.lang3.StringUtils;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 */
public final class AccountLogin {
    private AccountLogin() {
    }

    public static CompletableFuture<Void> login(Account account, ExecutorService executor,
                                                Consumer<Notification> notify) {
        String username = StringUtils.isBlank(account.getUsername()) ? "???" : account.getUsername();
        if (CookieAuth.MARKER.equals(account.getClientId())) {
            notify.accept(new Notification(TextFormatting.translate(String.format(
                    "&7Logging in with cookies... (%s)&r", username
            )), -1L));
            return CookieAuth.relogin(account, executor, message -> notify.accept(new Notification(
                            TextFormatting.translate(message) + " (" + username + ")", -1L
                    )))
                    .thenRun(() -> notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&aSuccessful login! (%s)&r", account.getUsername()
                    )), 5000L)))
                    .exceptionally(error -> {
                        notify.accept(new Notification(TextFormatting.translate(String.format(
                                "&c%s (%s)&r", error.getMessage(), username
                        )), 5000L));
                        return null;
                    });
        }
        AtomicReference<String> refreshToken = new AtomicReference<String>("");
        AtomicReference<String> accessToken = new AtomicReference<String>("");
        notify.accept(new Notification(TextFormatting.translate(String.format(
                "&7Fetching your Minecraft profile... (%s)&r", username
        )), -1L));
        MicrosoftAuth.CLIENT_ID = account.getClientId();
        MicrosoftAuth.SCOPE = account.getScope();
        return MicrosoftAuth.login(account.getAccessToken(), executor)
                .handle((session, error) -> {
                    if (session != null) {
                        account.setUsername(session.getUsername());
                        AccountManager.save();
                        SessionManager.set(session);
                        notify.accept(new Notification(TextFormatting.translate(String.format(
                                "&aSuccessful login! (%s)&r", account.getUsername()
                        )), 5000L));
                        return true;
                    }
                    return false;
                })
                .thenComposeAsync(completed -> {
                    if (completed) {
                        throw new NoSuchElementException();
                    }
                    notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&7Refreshing Microsoft access tokens... (%s)&r", username
                    )), -1L));
                    return MicrosoftAuth.refreshMSAccessTokens(account.getRefreshToken(), executor);
                })
                .thenComposeAsync(msAccessTokens -> {
                    notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&7Acquiring Xbox access token... (%s)&r", username
                    )), -1L));
                    refreshToken.set(msAccessTokens.get("refresh_token"));
                    return MicrosoftAuth.acquireXboxAccessToken(msAccessTokens.get("access_token"), executor);
                })
                .thenComposeAsync(xboxAccessToken -> {
                    notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&7Acquiring Xbox XSTS token... (%s)&r", username
                    )), -1L));
                    return MicrosoftAuth.acquireXboxXstsToken(xboxAccessToken, executor);
                })
                .thenComposeAsync(xboxXstsData -> {
                    notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&7Acquiring Minecraft access token... (%s)&r", username
                    )), -1L));
                    return MicrosoftAuth.acquireMCAccessToken(
                            xboxXstsData.get("Token"), xboxXstsData.get("uhs"), executor
                    );
                })
                .thenComposeAsync(mcToken -> {
                    notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&7Fetching your Minecraft profile... (%s)&r", username
                    )), -1L));
                    accessToken.set(mcToken);
                    return MicrosoftAuth.login(mcToken, executor);
                })
                .thenAccept(session -> {
                    account.setRefreshToken(refreshToken.get());
                    account.setAccessToken(accessToken.get());
                    account.setUsername(session.getUsername());
                    AccountManager.save();
                    SessionManager.set(session);
                    notify.accept(new Notification(TextFormatting.translate(String.format(
                            "&aSuccessful login! (%s)&r", account.getUsername()
                    )), 5000L));
                })
                .exceptionally(error -> {
                    if (!(error.getCause() instanceof NoSuchElementException)) {
                        notify.accept(new Notification(TextFormatting.translate(String.format(
                                "&c%s (%s)&r", error.getMessage(), username
                        )), 5000L));
                    }
                    return null;
                });
    }
}

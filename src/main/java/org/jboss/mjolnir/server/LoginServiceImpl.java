/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.mjolnir.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.sun.security.auth.login.ConfigFile;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.mjolnir.authentication.KerberosUser;
import org.jboss.mjolnir.client.LoginService;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Console;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author: navssurtani
 * @since: 0.1
 */

public class LoginServiceImpl extends RemoteServiceServlet implements LoginService {

    private Cache<String, KerberosUser> cache;

    public LoginServiceImpl() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.loaders().preload(true)
                .addFileCacheStore().location("/tmp/infinispan.store");
        builder.eviction().maxEntries(50);
        Configuration config = builder.build(true);
        EmbeddedCacheManager cacheManager = new DefaultCacheManager(config);
        cache = cacheManager.getCache();
    }

    // TODO: Change this implementation to make the password check on the cache first since it will be faster.
    @Override
    public KerberosUser login(String krb5Name, String githubName, String password) {
        try {
            validateCredentials(krb5Name, password);
        } catch (LoginException e) {
            // TODO: We have to handle retries if a password has been mis-typed.
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (URISyntaxException e) {
            // TODO: If this happens then there's something very very wrong happening.
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        KerberosUser toReturn = cache.get(krb5Name);
        if (toReturn == null) {
            toReturn = register(krb5Name, githubName, password);
            // TODO: The GitHub API work has to be done here as well now.
        }
        return toReturn;
    }

    @Override
    public KerberosUser loginFromSession() {
        KerberosUser kerberosUser = null;
        HttpServletRequest request = this.getThreadLocalRequest();
        HttpSession session = request.getSession();
        Object userObject = session.getAttribute("kerberosUser");
        if (userObject != null && userObject instanceof KerberosUser) {
            kerberosUser = (KerberosUser) userObject;
        }
        return kerberosUser;
    }

    @Override
    public void logout() {
        HttpServletRequest request = this.getThreadLocalRequest();
        HttpSession session = request.getSession(true);
        session.removeAttribute("kerberosUser");
    }

    private KerberosUser register(String krb5Name, String githubName, String password) {
        KerberosUser kerberosUser = new KerberosUser();
        kerberosUser.setName(krb5Name);
        kerberosUser.setGithubName(githubName);
        kerberosUser.setPwd(password);
        cache.put(krb5Name, kerberosUser);
        storeInSession(kerberosUser);
        return kerberosUser;
    }

    private void validateCredentials(final String krb5Name, final String password)
            throws LoginException, URISyntaxException{
        final Subject subject = null;
        final CallbackHandler callbackHandler = new CallbackHandler() {
            @Override
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback cb : callbacks) {
                    if (cb instanceof NameCallback) {
                        ((NameCallback) cb).setName(krb5Name);
                    } else if (cb instanceof PasswordCallback) {
                        ((PasswordCallback) cb).setPassword(password.toCharArray());
                    } else {
                        throw new IllegalStateException("Unknown callback.");
                    }
                }
            }
        };
        final javax.security.auth.login.Configuration loginConfiguration = new ConfigFile(this.getClass()
                .getResource("/jaas.config").toURI());
        final LoginContext loginContext = new LoginContext("Kerberos", subject, callbackHandler, loginConfiguration);
        loginContext.login();
    }

    private void storeInSession(KerberosUser kerberosUser) {
        HttpServletRequest httpServletRequest = this.getThreadLocalRequest();
        HttpSession session = httpServletRequest.getSession();
        session.setAttribute("kerberosUser", kerberosUser);
    }
}

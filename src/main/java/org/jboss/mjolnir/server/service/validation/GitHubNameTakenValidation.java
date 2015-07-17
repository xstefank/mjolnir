package org.jboss.mjolnir.server.service.validation;

import java.sql.SQLException;

import org.jboss.mjolnir.authentication.KerberosUser;
import org.jboss.mjolnir.client.exception.ApplicationException;
import org.jboss.mjolnir.client.domain.ValidationResult;
import org.jboss.mjolnir.server.bean.UserRepository;

/**
 * Verifies that given GH name is not used by different Mjolnir user
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class GitHubNameTakenValidation implements Validation<KerberosUser> {

    private UserRepository userRepository;

    public GitHubNameTakenValidation(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public ValidationResult validate(KerberosUser entity) {
        try {
            ValidationResult result = new ValidationResult();
            KerberosUser userByGitHubName = userRepository.getUserByGitHubName(entity.getGithubName());
            if (userByGitHubName != null && !userByGitHubName.equals(entity)) {
                result.addFailure("This GitHub name is already taken by different user.");
            }
            return result;
        } catch (SQLException e) {
            throw new ApplicationException(e);
        }
    }
}

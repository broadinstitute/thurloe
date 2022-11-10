Jira: \<Link to Jira ticket\>

  \<Don't forget to include the ticket number in the PR title.\>

What:

  \<For your reviewers' sake, please describe in a sentence or two what this PR is accomplishing (usually from the users' perspective, but not necessarily).\>

Why:

  \<For your reviewers' sake, please describe in ~1 paragraph what the value of this PR is to our users or to ourselves.\>

How:

  \<For your reviewers' sake, please describe in ~1 paragraph how this PR accomplishes its goal.\>

  \<If the PR is big, please indicate where a reviewer should start reading it (i.e. which file or function).\>

---

- [ ] **Submitter**: Make sure Swagger is updated if API changes
- [ ] **Submitter**: If updating admin endpoints, also update [firecloud-admin-cli](https://github.com/broadinstitute/firecloud-admin-cli)
- [ ] **Submitter**: Update FISMA documentation if changes to:
  * Authentication
  * Authorization
  * Encryption
  * Audit trails
- [ ] **Submitter**: If you're adding new libraries, sign us up to security updates for them

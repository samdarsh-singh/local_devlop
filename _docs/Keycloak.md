## OIDC Setup with Keycloak


### How to use it

1. **To start only Keycloak service:** Run `docker-compose up -d keycloak` to start only the Keycloak service.

2. **Access Keycloak Admin Console:** Navigate to `http://localhost:8080/admin/master/console/#/` in your web browser. Login with username `administrat0r` and password `password`.

3. **Realms, Clients, Roles, and Users:** Pre-configured a realm, a client, a role, and a user for testing purposes. You can review these in the Keycloak Admin Console.


## Add a new user

1. Open Keycloak by accessing `http://localhost:8080/admin/master/console/#/`.
2. Click on the `Users` tab on the left side menu.
3. Click on `Add user` button.
4. Fill out the details for the user.
5. Go to `Credentials` tab.
6. Set a new password and turn `Temporary` OFF.
7. Click on `Reset Password` button.
8. Go to `Role Mappings` tab.
9. In the `Available Roles` list select the roles you want to assign and click on `Add selected` button.

Absolutely, here is the updated README with a new section on JSON realm configuration, formatted in Markdown:

---

## JSON Realm Configuration

You can set up realms, clients, roles, and users by providing a JSON configuration file. The JSON file needs to be in a specific format that Keycloak understands.

Example of a realm configuration in JSON format:

```json
{
    "id": "{realm-id}",
    "realm": "{realm-name}",
    "enabled": true,
    "sslRequired": "{none/external/all}",
    "clients": [
        {
            "id": "{client-id}",
            "clientId": "{client-id}",
            "rootUrl": "{base-url}",
            "adminUrl": "{admin-url}",
            "surrogateAuthRequired": false,
            "enabled": true,
            "redirectUris": ["{redirect-uris}"],
            "webOrigins": ["{web-origins}"],
            "protocol": "openid-connect",
            "fullScopeAllowed": true,
            "secret": "{client-secret}",
            "authorizationServicesEnabled": true
        }
    ],
    "roles": {
        "realm": [
            {
                "id": "{role-id}",
                "name": "{role-name}",
                "description": "{role-description}",
                "composite": false
            }
        ]
    },
    "users": [
        {
            "username": "{username}",
            "enabled": true,
            "emailVerified": false,
            "credentials": [
                {
                    "type": "password",
                    "value": "{password}",
                    "temporary": false
                }
            ],
            "realmRoles": [
                "{role-name}"
            ]
        }
    ]
}
```

Replace the placeholders in the `{}` with your actual values.

### How to use the JSON file:

1. Save the JSON configuration to a file (e.g., `realms-config.json`) in the `_config` directory.
2. In the `docker-compose.yml` file, make sure that the `volumes` field under the `keycloak` service maps the JSON file into the container:

```yaml
volumes:
  - ./_config/realms-config.json:/opt/bitcloak/data/import/realm-export.json
```

3. Start or restart the Keycloak service. The service should detect the JSON file and import the realm configuration:

```bash
docker-compose up -d keycloak
```

4. Access the Keycloak Admin Console at `http://localhost:8080`. The imported realm, client, roles, and user should be visible.

### Troubleshooting:

Remember to check the Keycloak server logs if you run into any issues during the import process. You can access the logs with the command:

```bash
docker-compose logs keycloak
```

Please note that this process imports users without their passwords. You will need to manually set up the passwords for each user in the Keycloak Admin Console.

---

This guide should give you a basic understanding of how to set up a realm using a JSON configuration file. However, the full schema for the Keycloak import/export JSON file can be complex and beyond the scope of this README. For a complete reference, refer to the [Keycloak documentation](https://www.keycloak.org/docs/latest/server_admin/index.html#_export_import).

## Test user login

1. Open your application that uses OIDC for authentication
2. Click on the login button and you should be redirected to Keycloak login page.
3. Enter the username and password for the user you created.
4. You should be redirected back to your application and be logged in.

## User permissions

In the realm we created, there are defined two roles:

- user: Has access to basic functionality of the applications.
- admin: Has access to administrative features in addition to basic functionality.

You can assign these roles to the users when creating them or later by editing them in the `Role Mappings` tab.

## Troubleshooting

If you experience problems with the Keycloak setup:

- Make sure the docker service for Keycloak is running with `docker-compose ps`.
- Make sure the Keycloak port (default is 8090) is open in your firewall.
- Check the logs for Keycloak service with `docker-compose logs keycloak`.
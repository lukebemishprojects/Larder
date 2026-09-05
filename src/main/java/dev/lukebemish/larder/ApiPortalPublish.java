package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.DeploymentState;
import dev.lukebemish.larder.api.DeploymentStatus;
import dev.lukebemish.larder.api.PublishingType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.BackendConfigurationType;
import dev.lukebemish.larder.schema.Deployment;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.polymorphicsignatures.utilities.EnumUtils;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.ContentType;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiContentProperty;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static dev.lukebemish.larder.Api.connection;

final class ApiPortalPublish {
    @OpenApi(
        path = "/portal/{repository}/api/v1/publisher/upload",
        methods = HttpMethod.POST,
        description = "Create a bundle from a deployment, and optionally stage it for publication",
        pathParams = {@OpenApiParam(name = "repository", description = "Repository to publish to")},
        queryParams = {
            @OpenApiParam(name = "name", description = "Human-readable name for the bundle"),
            @OpenApiParam(name = "publishingType", type = PublishingType.class)
        },
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(
                mimeType = ContentType.FORM_DATA_MULTIPART,
                properties = @OpenApiContentProperty(
                    name = "bundle",
                    type = "string",
                    format = "binary"
                )
            )
        ),
        responses = @OpenApiResponse(
            status = "201",
            content = @OpenApiContent(
                mimeType = "text/plain;charset=UTF-8",
                from = UUID.class
            ),
            description = "Deployment ID"
        ),
        security = @OpenApiSecurity(name = "bearer"),
        tags = {"Portal Publishing"}
    )
    static void publisherUpload(Context context) throws SQLException {
        connection(context).transact(c -> {
            var roleId = MachineAuthenticator.machineRole(context, c);

            var repositories = c.select(new Repository.ByName(context.pathParam("repository")));
            if (repositories.isEmpty() || !repositories.getFirst().supportsPublishPortal()) {
                throw new NotFoundResponse();
            }
            var repository = repositories.getFirst();
            Identifier<Repository> repositoryId = Identifier.of(repository);

            var role = c.select(roleId);

            MachineAuthenticator.validateRole(roleId, repositoryId, c);
            MachineAuthenticator.validateRolePublish(roleId, c);

            PublishingType publishingType = EnumUtils.tryValueOf(Objects.requireNonNullElse(context.queryParam("publishingType"), "USER_MANAGED"));
            if (publishingType == null) {
                throw new BadRequestResponse();
            }

            var bundle = context.uploadedFile("bundle");
            if (bundle == null) {
                throw new BadRequestResponse();
            }

            var humanName = Objects.requireNonNullElse(context.queryParam("name"), bundle.filename());

            var deployment = new Deployment(
                UUID.randomUUID(),
                repositoryId,
                role.owner(),
                publishingType == PublishingType.AUTOMATIC,
                humanName,
                DeploymentState.PENDING,
                Optional.empty(),
                Optional.of(roleId)
            );

            var backend = Backend.configuredBackend(repositoryId, BackendConfigurationType.DEPLOYMENTS, c);

            try (var os = backend.writePath(deployment.id() + ".zip");
                 var is = bundle.content()) {
                is.transferTo(os);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            c.insert(deployment);
        });
    }

    @OpenApi(
        path = "/portal/{repository}/api/v1/publisher/status",
        methods = HttpMethod.POST,
        description = "Check the status of an existing deployment",
        pathParams = @OpenApiParam(name = "repository", description = "Repository of deployment"),
        queryParams = {
            @OpenApiParam(name = "id", description = "Deployment ID", type = UUID.class, required = true)
        },
        responses = {
            @OpenApiResponse(
                status = "202",
                content = @OpenApiContent(
                    from = DeploymentStatus.class
                )
            ),
            @OpenApiResponse(status = "404", description = "Deployment not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "bearer"),
        tags = {"Portal Publishing"}
    )
    static void publisherStatus(Context context) {
        // TODO: implement
    }

    @OpenApi(
        path = "/portal/{repository}/api/v1/publisher/deployment/{id}",
        methods = HttpMethod.POST,
        description = "Publish a deployment",
        pathParams = {
            @OpenApiParam(name = "repository", description = "Repository of deployment"),
            @OpenApiParam(name = "id", description = "Deployment ID", type = UUID.class)
        },
        responses = {
            @OpenApiResponse(status = "204", description = "Deployment publishing"),
            @OpenApiResponse(status = "404", description = "Deployment not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "bearer"),
        tags = {"Portal Publishing"}
    )
    static void publisherDeploymentPublish(Context context) {
        // TODO: implement
    }

    @OpenApi(
        path = "portal/{repository}/api/v1/publisher/deployment/{id}",
        methods = HttpMethod.DELETE,
        description = "Delete a deployment",
        pathParams = {
            @OpenApiParam(name = "repository", description = "Repository of deployment"),
            @OpenApiParam(name = "id", description = "Deployment ID", type = UUID.class)
        },
        responses = {
            @OpenApiResponse(status = "204", description = "Deployment deleted"),
            @OpenApiResponse(status = "404", description = "Deployment not found", content = @OpenApiContent(from = ApiError.class))
        },
        security = @OpenApiSecurity(name = "bearer"),
        tags = {"Portal Publishing"}
    )
    static void publisherDeploymentDelete(Context context) {
        // TODO: implement
    }
}

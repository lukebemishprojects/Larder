package dev.lukebemish.larder;

import dev.lukebemish.larder.api.ApiError;
import dev.lukebemish.larder.api.DeploymentStatus;
import dev.lukebemish.larder.api.PublishingType;
import io.javalin.http.Context;
import io.javalin.openapi.ContentType;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiContentProperty;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.util.UUID;

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
    static void publisherUpload(Context context) {
        // TODO: implement
    }

    @OpenApi(
        path = "/portal/{repository}/api/v1/publisher/status",
        methods = HttpMethod.POST,
        description = "Check the status of an existing deployment",
        pathParams = {@OpenApiParam(name = "repository", description = "Repository of deployment")},
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

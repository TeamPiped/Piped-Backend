package me.kavin.piped.server.handlers.auth;

import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.DatabaseHelper;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.obj.db.User;
import me.kavin.piped.utils.resp.SimpleErrorMessage;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.ByteArrayInputStream;

import static me.kavin.piped.consts.Constants.mapper;

public class StorageHandlers {

    public static byte[] statFile(String session, String name) throws Exception {

        if (Constants.S3_CLIENT == null)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Storage is not configured on this instance!"));

        if (!StringUtils.isAlphanumeric(name) || name.length() > 32)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid path provided!"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid session provided!"));

        try {
            var statData = Constants.S3_CLIENT.statObject(
                    StatObjectArgs.builder()
                            .bucket(Constants.S3_BUCKET)
                            .object(user.getId() + "/" + name)
                            .build()
            );

            return mapper.writeValueAsBytes(
                    mapper.createObjectNode()
                            .put("status", "exists")
                            .put("etag", statData.etag())
                            .put("date", statData.lastModified().toInstant().toEpochMilli())
            );
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey"))
                return mapper.writeValueAsBytes(
                        mapper.createObjectNode()
                                .put("status", "not_exists")
                );
            else
                throw e;
        }
    }

    public static byte[] putFile(String session, String name, String etag, byte[] content) throws Exception {

        if (Constants.S3_CLIENT == null)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Storage is not configured on this instance!"));

        if (!StringUtils.isAlphanumeric(name) || name.length() > 32)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid path provided!"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid session provided!"));

        // check if file size is greater than 500kb
        if (content.length > 500 * 1024)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("File size is too large!"));

        // check if file already exists, if it does, check if the etag matches
        try {
            var statData = Constants.S3_CLIENT.statObject(
                    StatObjectArgs.builder()
                            .bucket(Constants.S3_BUCKET)
                            .object(user.getId() + "/" + name)
                            .build()
            );

            if (!statData.etag().equals(etag))
                ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid etag provided! (File uploaded by another client?)"));

        } catch (ErrorResponseException e) {
            if (!e.errorResponse().code().equals("NoSuchKey"))
                ExceptionUtils.rethrow(e);
        }

        var stream = new ByteArrayInputStream(content);

        Constants.S3_CLIENT.putObject(
                PutObjectArgs.builder()
                        .bucket(Constants.S3_BUCKET)
                        .object(user.getId() + "/" + name)
                        .stream(stream, content.length, -1)
                        .build()
        );

        return mapper.writeValueAsBytes(
                mapper.createObjectNode()
                        .put("status", "ok")
        );
    }

    public static byte[] getFile(String session, String name) throws Exception {

        if (Constants.S3_CLIENT == null)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Storage is not configured on this instance!"));

        if (!StringUtils.isAlphanumeric(name) || name.length() > 32)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid path provided!"));

        User user = DatabaseHelper.getUserFromSession(session);

        if (user == null)
            ExceptionHandler.throwErrorResponse(new SimpleErrorMessage("Invalid session provided!"));

        try (var stream = Constants.S3_CLIENT.getObject(GetObjectArgs.builder()
                .bucket(Constants.S3_BUCKET)
                .object(user.getId() + "/" + name)
                .build())) {
            return IOUtils.toByteArray(stream);
        }
    }
}

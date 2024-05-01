package com.example.backend.entity.maria;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


/**
 * QUser is a Querydsl query type for User
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QUser extends EntityPathBase<User> {

    private static final long serialVersionUID = -1570723517L;

    public static final QUser user = new QUser("user");

    public final EnumPath<com.example.backend.entity.maria.enumData.Authority> authority = createEnum("authority", com.example.backend.entity.maria.enumData.Authority.class);

    public final StringPath dept = createString("dept");

    public final StringPath email = createString("email");

    public final StringPath name = createString("name");

    public final StringPath password = createString("password");

    public final StringPath position = createString("position");

    public final StringPath profile_image_path = createString("profile_image_path");

    public final StringPath tel = createString("tel");

    public final NumberPath<Long> userId = createNumber("userId", Long.class);

    public QUser(String variable) {
        super(User.class, forVariable(variable));
    }

    public QUser(Path<? extends User> path) {
        super(path.getType(), path.getMetadata());
    }

    public QUser(PathMetadata metadata) {
        super(User.class, metadata);
    }

}


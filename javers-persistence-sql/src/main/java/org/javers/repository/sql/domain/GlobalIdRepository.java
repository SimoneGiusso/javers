package org.javers.repository.sql.domain;

import org.javers.common.collections.Optional;
import org.javers.core.json.JsonConverter;
import org.javers.core.metamodel.object.GlobalId;
import org.javers.repository.sql.infrastructure.poly.JaversPolyJDBC;
import org.polyjdbc.core.query.InsertQuery;
import org.polyjdbc.core.query.SelectQuery;
import org.polyjdbc.core.query.mapper.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.javers.repository.sql.domain.FixedSchemaFactory.CDO_CLASS_PK;
import static org.javers.repository.sql.domain.FixedSchemaFactory.CDO_CLASS_QUALIFIED_NAME;
import static org.javers.repository.sql.domain.FixedSchemaFactory.CDO_CLASS_TABLE_NAME;
import static org.javers.repository.sql.domain.FixedSchemaFactory.CDO_PK_SEQ_NAME;
import static org.javers.repository.sql.domain.FixedSchemaFactory.GLOBAL_ID_CLASS_FK;
import static org.javers.repository.sql.domain.FixedSchemaFactory.GLOBAL_ID_LOCAL_ID;
import static org.javers.repository.sql.domain.FixedSchemaFactory.GLOBAL_ID_PK;
import static org.javers.repository.sql.domain.FixedSchemaFactory.GLOBAL_ID_PK_SEQ;
import static org.javers.repository.sql.domain.FixedSchemaFactory.GLOBAL_ID_TABLE_NAME;

public class GlobalIdRepository {

    private static final String NATIVE_QUERY = GLOBAL_ID_TABLE_NAME + "." + GLOBAL_ID_CLASS_FK + " = " + CDO_CLASS_TABLE_NAME + "." + CDO_CLASS_PK
            + " AND " + GLOBAL_ID_TABLE_NAME + "." + GLOBAL_ID_LOCAL_ID + " = '%s'";

    private JaversPolyJDBC javersPolyjdbc;
    private JsonConverter jsonConverter;

    public GlobalIdRepository(JaversPolyJDBC javersPolyjdbc) {
        this.javersPolyjdbc = javersPolyjdbc;
    }

    public long save(GlobalId globalId) {
        Optional<Long> lookup = getIfExists(globalId);

        return lookup.isPresent() ? lookup.get() : insert(globalId);
    }

    private Optional<Long> getIfExists(GlobalId globalId) {
        SelectQuery selectQuery = javersPolyjdbc.query()
                .select(GLOBAL_ID_PK)
                .from(GLOBAL_ID_TABLE_NAME + "," + CDO_CLASS_TABLE_NAME)
                .where(String.format(NATIVE_QUERY, jsonConverter.toJson(globalId.getCdoId())));

        return Optional.fromNullable(javersPolyjdbc.queryRunner().queryUnique(selectQuery, new ObjectMapper<Long>() {
            @Override
            public Long createObject(ResultSet resultSet) throws SQLException {
                return resultSet.getLong(GLOBAL_ID_PK);
            }
        }, false));
    }

    private Long insert(GlobalId globalId) {
        InsertQuery insertClassQuery = javersPolyjdbc.query()
                .insert()
                .into(CDO_CLASS_TABLE_NAME)
                .value(CDO_CLASS_QUALIFIED_NAME, globalId.getCdoClass().getClientsClass().getName())
                .sequence(CDO_CLASS_PK, CDO_PK_SEQ_NAME);

        long insertedClassId = javersPolyjdbc.queryRunner().insert(insertClassQuery);

        InsertQuery insertGlobalIdQuery = javersPolyjdbc.query()
                .insert()
                .into(GLOBAL_ID_TABLE_NAME)
                .value(GLOBAL_ID_LOCAL_ID, jsonConverter.toJson(globalId.getCdoId()))
                .value(GLOBAL_ID_CLASS_FK, insertedClassId)
                .sequence(GLOBAL_ID_PK, GLOBAL_ID_PK_SEQ);

        return javersPolyjdbc.queryRunner().insert(insertGlobalIdQuery);
    }

    public void setJsonConverter(JsonConverter JSONConverter) {
        this.jsonConverter = JSONConverter;
    }
}

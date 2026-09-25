package db.migration;

import com.pedeai.payment.domain.DefaultPaymentMethods;
import com.pedeai.shared.id.UuidV7;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Formas de pagamento padrão para as lojas criadas antes da Etapa 2. As lojas novas ganham as suas no cadastro.
 * Em Java porque gerar UUID em SQL não é igual no H2 e no PostgreSQL.
 */
public class V4__create_default_payment_methods extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<UUID> stores = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT s.id FROM store s WHERE NOT EXISTS (SELECT 1 FROM payment_method m WHERE m.store_id = s.id)");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                stores.add(rows.getObject(1, UUID.class));
            }
        }
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO payment_method (id, store_id, name, type, active, sort_order, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, TRUE, ?, ?, ?, 0)""")) {
            for (UUID store : stores) {
                for (int position = 0; position < DefaultPaymentMethods.ALL.size(); position++) {
                    DefaultPaymentMethods.Entry entry = DefaultPaymentMethods.ALL.get(position);
                    insert.setObject(1, UuidV7.generate());
                    insert.setObject(2, store);
                    insert.setString(3, entry.name());
                    insert.setString(4, entry.type().name());
                    insert.setInt(5, position);
                    insert.setTimestamp(6, now);
                    insert.setTimestamp(7, now);
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }
}

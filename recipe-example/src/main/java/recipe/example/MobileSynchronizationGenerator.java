package recipe.example;

import java.io.IOException;
import java.sql.SQLException;

public class MobileSynchronizationGenerator extends msync.codegeneration.MobileSynchronizationGenerator {

    public static void main(String[] args) throws SQLException, IOException {
        new MobileSynchronizationGenerator().generate();
    }
}

package com.recipeguide.db;

import com.recipeguide.model.Recipe;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:recipe_guide.db";

    public static void initializeDatabase() {
        String createKategoriler = "CREATE TABLE IF NOT EXISTS kategoriler (kategori_id INTEGER PRIMARY KEY AUTOINCREMENT, kategori_adi TEXT NOT NULL UNIQUE);";
        String createTarifler = "CREATE TABLE IF NOT EXISTS tarifler (tarif_id INTEGER PRIMARY KEY AUTOINCREMENT, tarif_adi TEXT NOT NULL, kategori_id INTEGER, hazirlama_suresi INTEGER, talimatlar TEXT, FOREIGN KEY (kategori_id) REFERENCES kategoriler(kategori_id));";
        String createMalzemeler = "CREATE TABLE IF NOT EXISTS malzemeler (malzeme_id INTEGER PRIMARY KEY AUTOINCREMENT, malzeme_adi TEXT NOT NULL UNIQUE, toplam_miktar TEXT, malzeme_birim TEXT);";
        String createTarifMalzemeler = "CREATE TABLE IF NOT EXISTS tarif_malzemeler (tarif_id INTEGER, malzeme_id INTEGER, malzeme_miktar REAL, PRIMARY KEY (tarif_id, malzeme_id), FOREIGN KEY (tarif_id) REFERENCES tarifler(tarif_id), FOREIGN KEY (malzeme_id) REFERENCES malzemeler(malzeme_id));";
        String insertKategoriler = "INSERT OR IGNORE INTO kategoriler (kategori_adi) VALUES ('Ana Yemek'),('Tatlı'),('Çorba'),('Salata'),('Meze'),('Kahvaltı'),('İçecek');";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createKategoriler);
            stmt.execute(createTarifler);
            stmt.execute(createMalzemeler);
            stmt.execute(createTarifMalzemeler);
            stmt.execute(insertKategoriler);
            System.out.println("Veritabanı tabloları başarıyla oluşturuldu.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveRecipe(Recipe recipe) {
        String insertRecipeSQL = """
            INSERT INTO tarifler (tarif_adi, kategori_id, hazirlama_suresi, talimatlar)
            VALUES (?, (SELECT kategori_id FROM kategoriler WHERE kategori_adi = ?), ?, ?)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertRecipeSQL, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, recipe.getName());
            pstmt.setString(2, recipe.getCategory());
            pstmt.setInt(3, recipe.getPreparationTime());
            pstmt.setString(4, recipe.getInstructions());
            
            pstmt.executeUpdate();
            
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int recipeId = generatedKeys.getInt(1);
                    for (Recipe.RecipeIngredient ingredient : recipe.getIngredients()) {
                        ensureIngredientExists(conn, ingredient);
                    }
                    saveIngredients(recipeId, recipe.getIngredients());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void ensureIngredientExists(Connection conn, Recipe.RecipeIngredient ingredient) throws SQLException {
        String checkSQL = "SELECT malzeme_id FROM malzemeler WHERE malzeme_adi = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
            checkStmt.setString(1, ingredient.getName());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    String insertSQL = "INSERT INTO malzemeler (malzeme_adi, toplam_miktar, malzeme_birim) VALUES (?, '', ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
                        insertStmt.setString(1, ingredient.getName());
                        insertStmt.setString(2, ingredient.getUnit());
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    private static void saveIngredients(int recipeId, List<Recipe.RecipeIngredient> ingredients) {
        String insertIngredientSQL = """
            INSERT INTO tarif_malzemeler (tarif_id, malzeme_id, malzeme_miktar)
            VALUES (?, (SELECT malzeme_id FROM malzemeler WHERE malzeme_adi = ?), ?)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertIngredientSQL)) {
            
            for (Recipe.RecipeIngredient ingredient : ingredients) {
                pstmt.setInt(1, recipeId);
                pstmt.setString(2, ingredient.getName());
                pstmt.setDouble(3, ingredient.getAmount());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Recipe> getAllRecipes() {
        List<Recipe> recipes = new ArrayList<>();
        String selectSQL = """
            SELECT t.tarif_id, t.tarif_adi, k.kategori_adi, t.hazirlama_suresi, t.talimatlar
            FROM tarifler t
            JOIN kategoriler k ON t.kategori_id = k.kategori_id
            ORDER BY t.tarif_adi
            """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {
            
            while (rs.next()) {
                int id = rs.getInt("tarif_id");
                String name = rs.getString("tarif_adi");
                String category = rs.getString("kategori_adi");
                int prepTime = rs.getInt("hazirlama_suresi");
                String instructions = rs.getString("talimatlar");
                
                List<Recipe.RecipeIngredient> ingredients = getIngredientsForRecipe(id);
                
                Recipe recipe = new Recipe(id, name, category, prepTime, instructions);
                recipe.setIngredients(ingredients);
                recipes.add(recipe);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return recipes;
    }

    private static List<Recipe.RecipeIngredient> getIngredientsForRecipe(int recipeId) {
        List<Recipe.RecipeIngredient> ingredients = new ArrayList<>();
        String selectSQL = """
            SELECT m.malzeme_adi, tm.malzeme_miktar, m.malzeme_birim
            FROM tarif_malzemeler tm
            JOIN malzemeler m ON tm.malzeme_id = m.malzeme_id
            WHERE tm.tarif_id = ?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            
            pstmt.setInt(1, recipeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("malzeme_adi");
                    double amount = rs.getDouble("malzeme_miktar");
                    String unit = rs.getString("malzeme_birim");
                    
                    ingredients.add(new Recipe.RecipeIngredient(name, amount, unit));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ingredients;
    }

    public static void deleteRecipe(int recipeId) {
        String deleteTarifMalzemeler = "DELETE FROM tarif_malzemeler WHERE tarif_id = ?";
        String deleteTarif = "DELETE FROM tarifler WHERE tarif_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt1 = conn.prepareStatement(deleteTarifMalzemeler);
             PreparedStatement pstmt2 = conn.prepareStatement(deleteTarif)) {
            pstmt1.setInt(1, recipeId);
            pstmt1.executeUpdate();
            pstmt2.setInt(1, recipeId);
            pstmt2.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }
} 
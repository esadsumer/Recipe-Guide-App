package com.recipeguide.database;

import com.recipeguide.model.Recipe;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RecipeDatabaseService {
    
    public static void initializeDatabase() {
        String createTableSQL = """
            -- Kategoriler tablosu
            CREATE TABLE IF NOT EXISTS kategoriler (
                kategori_id INTEGER PRIMARY KEY AUTOINCREMENT,
                kategori_adi TEXT NOT NULL UNIQUE
            );
            
            -- Tarifler tablosu
            CREATE TABLE IF NOT EXISTS tarifler (
                tarif_id INTEGER PRIMARY KEY AUTOINCREMENT,
                tarif_adi TEXT NOT NULL,
                kategori_id INTEGER,
                hazirlama_suresi INTEGER,
                talimatlar TEXT,
                FOREIGN KEY (kategori_id) REFERENCES kategoriler(kategori_id)
            );
            
            -- Malzemeler tablosu
            CREATE TABLE IF NOT EXISTS malzemeler (
                malzeme_id INTEGER PRIMARY KEY AUTOINCREMENT,
                malzeme_adi TEXT NOT NULL UNIQUE,
                toplam_miktar TEXT,
                malzeme_birim TEXT
            );
            
            -- Tarif-Malzeme ilişki tablosu
            CREATE TABLE IF NOT EXISTS tarif_malzemeler (
                tarif_id INTEGER,
                malzeme_id INTEGER,
                malzeme_miktar REAL,
                PRIMARY KEY (tarif_id, malzeme_id),
                FOREIGN KEY (tarif_id) REFERENCES tarifler(tarif_id),
                FOREIGN KEY (malzeme_id) REFERENCES malzemeler(malzeme_id)
            );
            
            -- Varsayılan kategorileri ekle
            INSERT OR IGNORE INTO kategoriler (kategori_adi) VALUES 
            ('Ana Yemek'),
            ('Tatlı'),
            ('Çorba'),
            ('Salata'),
            ('Meze'),
            ('Kahvaltı'),
            ('İçecek');
            """;
            
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Kategori işlemleri
    public static void addKategori(String kategoriAdi) {
        String sql = "INSERT INTO kategoriler (kategori_adi) VALUES (?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, kategoriAdi);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getAllKategoriler() {
        List<String> kategoriler = new ArrayList<>();
        String sql = "SELECT kategori_adi FROM kategoriler ORDER BY kategori_adi";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                kategoriler.add(rs.getString("kategori_adi"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return kategoriler;
    }

    // Malzeme işlemleri
    public static void addMalzeme(String malzemeAdi, String toplamMiktar, String birim) {
        String sql = "INSERT INTO malzemeler (malzeme_adi, toplam_miktar, malzeme_birim) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, malzemeAdi);
            pstmt.setString(2, toplamMiktar);
            pstmt.setString(3, birim);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getAllMalzemeler() {
        List<String> malzemeler = new ArrayList<>();
        String sql = "SELECT malzeme_adi FROM malzemeler ORDER BY malzeme_adi";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                malzemeler.add(rs.getString("malzeme_adi"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return malzemeler;
    }

    // Tarif işlemleri
    public static void addTarif(String tarifAdi, String kategoriAdi, int hazirlamaSuresi, String talimatlar) {
        String sql = """
            INSERT INTO tarifler (tarif_adi, kategori_id, hazirlama_suresi, talimatlar)
            VALUES (?, (SELECT kategori_id FROM kategoriler WHERE kategori_adi = ?), ?, ?)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, tarifAdi);
            pstmt.setString(2, kategoriAdi);
            pstmt.setInt(3, hazirlamaSuresi);
            pstmt.setString(4, talimatlar);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addTarifMalzeme(int tarifId, String malzemeAdi, double miktar) {
        String sql = """
            INSERT INTO tarif_malzemeler (tarif_id, malzeme_id, malzeme_miktar)
            VALUES (?, (SELECT malzeme_id FROM malzemeler WHERE malzeme_adi = ?), ?)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tarifId);
            pstmt.setString(2, malzemeAdi);
            pstmt.setDouble(3, miktar);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getTarifMalzemeler(int tarifId) {
        List<String> malzemeler = new ArrayList<>();
        String sql = """
            SELECT m.malzeme_adi, tm.malzeme_miktar, m.malzeme_birim
            FROM tarif_malzemeler tm
            JOIN malzemeler m ON tm.malzeme_id = m.malzeme_id
            WHERE tm.tarif_id = ?
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tarifId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String malzeme = String.format("%s - %.2f %s",
                        rs.getString("malzeme_adi"),
                        rs.getDouble("malzeme_miktar"),
                        rs.getString("malzeme_birim"));
                    malzemeler.add(malzeme);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return malzemeler;
    }

    public static List<String> getAllTarifler() {
        List<String> tarifler = new ArrayList<>();
        String sql = """
            SELECT t.tarif_id, t.tarif_adi, k.kategori_adi, t.hazirlama_suresi
            FROM tarifler t
            JOIN kategoriler k ON t.kategori_id = k.kategori_id
            ORDER BY t.tarif_adi
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String tarif = String.format("%d - %s (%s) - %d dakika",
                    rs.getInt("tarif_id"),
                    rs.getString("tarif_adi"),
                    rs.getString("kategori_adi"),
                    rs.getInt("hazirlama_suresi"));
                tarifler.add(tarif);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tarifler;
    }
} 
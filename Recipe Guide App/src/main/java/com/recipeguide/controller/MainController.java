package com.recipeguide.controller;

import com.recipeguide.model.Recipe;
import com.recipeguide.db.DatabaseManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import javafx.beans.property.SimpleDoubleProperty;
import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import javafx.application.Platform;

public class MainController {
    @FXML private TextField nameField;
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField prepTimeField;
    @FXML private TextArea instructionsArea;
    @FXML private ComboBox<String> existingIngredientsComboBox;
    @FXML private TextField ingredientAmountField;
    @FXML private ComboBox<String> ingredientUnitComboBox;
    @FXML private TextField ingredientPriceField;
    @FXML private TableView<Recipe> recipeTable;
    @FXML private TableColumn<Recipe, String> nameColumn;
    @FXML private TableColumn<Recipe, String> categoryColumn;
    @FXML private TableColumn<Recipe, Integer> prepTimeColumn;
    @FXML private TableColumn<Recipe, String> instructionsColumn;
    @FXML private TableColumn<Recipe, String> ingredientsColumn;
    @FXML private TableColumn<Recipe, Double> matchPercentageColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterCategoryComboBox;
    @FXML private TextArea recipeDetailArea;
    @FXML private Button addNewIngredientButton;
    @FXML private TextField ingredientSearchField;
    @FXML private ListView<String> availableIngredientsList;
    @FXML private ListView<String> selectedIngredientsList;
    @FXML private Button addIngredientToSearchButton;
    @FXML private Button removeIngredientFromSearchButton;
    @FXML private TextField newIngredientNameField;

    private ObservableList<Recipe> recipes = FXCollections.observableArrayList();
    private ObservableList<String> ingredients = FXCollections.observableArrayList();
    private ObservableList<String> availableIngredients = FXCollections.observableArrayList();
    private ObservableList<String> selectedIngredients = FXCollections.observableArrayList();
    private Map<Integer, Double> recipeMatchPercentages = new HashMap<>();

    @FXML
    public void initialize() {
        // Tablo sütunlarını ayarla
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        categoryColumn.setCellValueFactory(cellData -> cellData.getValue().categoryProperty());
        prepTimeColumn.setCellValueFactory(cellData -> cellData.getValue().preparationTimeProperty().asObject());
        instructionsColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getInstructions()));
        ingredientsColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getIngredientsAsString()));
        matchPercentageColumn.setCellValueFactory(cellData -> {
            Recipe recipe = cellData.getValue();
            Double matchPercentage = recipeMatchPercentages.getOrDefault(recipe.getId(), 0.0);
            return new SimpleDoubleProperty(matchPercentage).asObject();
        });

        // Add color coding for recipe rows
        recipeTable.setRowFactory(tv -> new TableRow<Recipe>() {
            @Override
            protected void updateItem(Recipe recipe, boolean empty) {
                super.updateItem(recipe, empty);
                if (empty || recipe == null) {
                    setStyle("");
                } else {
                    double matchPercentage = recipeMatchPercentages.getOrDefault(recipe.getId(), 0.0);
                    if (matchPercentage == 100.0) {
                        setStyle("-fx-background-color: #90EE90;"); // Light green
                    } else if (matchPercentage < 50.0) {
                        setStyle("-fx-background-color: #FFB6C1;"); // Light red
                    } else {
                        setStyle("-fx-background-color: #FFD700;"); // Gold for partial matches
                    }
                }
            }
        });

        // Kategori ComboBox'ını doldur
        categoryComboBox.setItems(FXCollections.observableArrayList(
            "Ana Yemek", "Tatlı", "Çorba", "Salata", "Meze", "Kahvaltı", "İçecek"
        ));

        // Malzeme birimleri ComboBox'ını doldur
        ingredientUnitComboBox.setItems(FXCollections.observableArrayList(
            "gram", "kg", "ml", "lt", "adet", "yemek kaşığı", "çay kaşığı", "su bardağı"
        ));

        // Mevcut malzemeleri veritabanından yükle
        loadExistingIngredients();

        // Tarif tablosunu ayarla
        recipeTable.setItems(recipes);
        loadRecipes();

        filterCategoryComboBox.setItems(FXCollections.observableArrayList(
            "Ana Yemek", "Tatlı", "Çorba", "Salata", "Meze", "Kahvaltı", "İçecek"
        ));

        // Mevcut malzeme seçildiğinde birim alanını otomatik doldur
        existingIngredientsComboBox.setOnAction(e -> {
            String selectedIngredient = existingIngredientsComboBox.getValue();
            if (selectedIngredient != null) {
                // Veritabanından malzemenin birimini al
                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "SELECT malzeme_birim FROM malzemeler WHERE malzeme_adi = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, selectedIngredient);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                String unit = rs.getString("malzeme_birim");
                                if (unit != null && !unit.isEmpty()) {
                                    ingredientUnitComboBox.setValue(unit);
                                }
                            }
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // Initialize new components
        availableIngredientsList.setItems(availableIngredients);
        selectedIngredientsList.setItems(selectedIngredients);
        
        // Load available ingredients
        loadAvailableIngredients();
    }

    @FXML
    private void handleAddIngredient() {
        String name = existingIngredientsComboBox.getValue();
        String amount = ingredientAmountField.getText();
        String unit = ingredientUnitComboBox.getValue();

        if (name == null || name.isEmpty()) {
            showAlert("Hata", "Malzeme adı boş olamaz.");
            return;
        }
        if (amount == null || amount.isEmpty()) {
            showAlert("Hata", "Malzeme miktarı boş olamaz.");
            return;
        }
        if (unit == null || unit.isEmpty()) {
            showAlert("Hata", "Malzeme birimi seçilmelidir.");
            return;
        }
        try {
            Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            showAlert("Hata", "Malzeme miktarı için lütfen sadece sayı girin (örn. 2 veya 1.5).");
            return;
        }

        // Sadece doğru formatta ekle
        double amountValue = Double.parseDouble(amount);
        String ingredient = String.format("%s - %.2f %s", name, amountValue, unit);
        if (!ingredients.contains(ingredient)) {
            ingredients.add(ingredient);
        }
        existingIngredientsComboBox.setValue(null);
        ingredientAmountField.clear();
        ingredientUnitComboBox.setValue(null);
    }

    @FXML
    private void handleAddNewIngredient() {
        String name = newIngredientNameField.getText();
        String unit = ingredientUnitComboBox.getValue();

        if (name == null || name.isEmpty()) {
            showAlert("Hata", "Malzeme adı boş olamaz.");
            return;
        }
        if (unit == null || unit.isEmpty()) {
            showAlert("Hata", "Malzeme birimi seçilmelidir.");
            return;
        }

        // Veritabanına yeni malzemeyi ekle
        try (Connection conn = DatabaseManager.getConnection()) {
            String insertSQL = "INSERT OR IGNORE INTO malzemeler (malzeme_adi, malzeme_birim) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                pstmt.setString(1, name);
                pstmt.setString(2, unit);
                pstmt.executeUpdate();
                showAlert("Başarılı", "Yeni malzeme başarıyla eklendi.");
                loadExistingIngredients();
                loadAvailableIngredients();
                newIngredientNameField.clear();
                ingredientUnitComboBox.setValue(null);
            }
        } catch (SQLException e) {
            showAlert("Hata", "Malzeme eklenirken bir hata oluştu: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteIngredient() {
        String selected = selectedIngredientsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedIngredients.remove(selected);
        } else {
            showAlert("Hata", "Lütfen silmek için bir malzeme seçin.");
        }
    }

    @FXML
    private void handleSaveRecipe() {
        String name = nameField.getText();
        String category = categoryComboBox.getValue();
        String prepTime = prepTimeField.getText();
        String instructions = instructionsArea.getText();

        if (name == null || name.isEmpty()) {
            showAlert("Hata", "Tarif adı boş olamaz.");
            return;
        }
        if (category == null || category.isEmpty()) {
            showAlert("Hata", "Kategori seçilmelidir.");
            return;
        }
        if (prepTime == null || prepTime.isEmpty()) {
            showAlert("Hata", "Hazırlama süresi boş olamaz.");
            return;
        }
        try {
            int prepTimeValue = Integer.parseInt(prepTime);
            Recipe recipe = new Recipe(0, name, category, prepTimeValue, instructions);
            StringBuilder hataliMalzemeler = new StringBuilder();
            // Tüm hatalı malzemeleri toplayıp ListView'dan siliyoruz
            List<String> toRemove = new java.util.ArrayList<>();
            for (String ingredient : new java.util.ArrayList<>(ingredients)) {
                String[] parts = ingredient.split(" - ", 2);
                String ingredientName = parts.length > 0 ? parts[0].trim() : "";
                double amount = 0;
                String unit = "";
                boolean valid = true;
                if (parts.length == 2) {
                    String rightPart = parts[1].trim();
                    // Nokta veya virgül ondalık ayracı desteği
                    String[] amountParts = rightPart.split(" ", 2);
                    if (amountParts.length == 2) {
                        try {
                            String amountStr = amountParts[0].replace(",", ".");
                            amount = Double.parseDouble(amountStr);
                        } catch (NumberFormatException e) {
                            valid = false;
                        }
                        unit = amountParts[1].trim();
                    } else {
                        valid = false;
                    }
                } else {
                    valid = false;
                }
                if (ingredientName != null && !ingredientName.isEmpty() && valid) {
                    recipe.getIngredients().add(new Recipe.RecipeIngredient(ingredientName, amount, unit));
                } else if (!valid) {
                    hataliMalzemeler.append(ingredient).append("\n");
                }
            }
            // Hatalı malzemeleri ListView'dan sil
            // ingredients.removeAll(toRemove);
            DatabaseManager.saveRecipe(recipe);
            clearFields();
            loadRecipes();
            if (hataliMalzemeler.length() > 0) {
                showAlert("Uyarı", "Bazı malzemeler kaydedilmedi ve listeden silindi:\n" + hataliMalzemeler.toString());
            } else {
                showAlert("Başarılı", "Tarif başarıyla kaydedildi.");
            }
        } catch (NumberFormatException e) {
            showAlert("Hata", "Hazırlama süresi için geçerli bir sayı girin.");
        }
    }

    @FXML
    private void handleDeleteRecipe() {
        Recipe selectedRecipe = recipeTable.getSelectionModel().getSelectedItem();
        if (selectedRecipe == null) {
            showAlert("Hata", "Lütfen silmek için bir tarif seçin.");
            return;
        }
        // Onay iste
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Tarifi Sil");
        alert.setHeaderText(null);
        alert.setContentText("Seçili tarifi silmek istediğinize emin misiniz?");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            DatabaseManager.deleteRecipe(selectedRecipe.getId());
            loadRecipes();
            showAlert("Başarılı", "Tarif silindi.");
        }
    }

    @FXML
    private void handleAddRecipe() {
        // Tarif ekleme işlemi için yeni bir pencere açılabilir veya mevcut alanlar kullanılabilir
        // Örnek: showAddRecipeDialog();
    }

    @FXML
    private void handleUpdateRecipe() {
        Recipe selectedRecipe = recipeTable.getSelectionModel().getSelectedItem();
        if (selectedRecipe == null) {
            showAlert("Hata", "Lütfen güncellemek için bir tarif seçin.");
            return;
        }
        // Tarif güncelleme işlemi için yeni bir pencere açılabilir veya mevcut alanlar kullanılabilir
        // Örnek: showUpdateRecipeDialog(selectedRecipe);
    }

    @FXML
    private void handleSearch() {
        String searchText = searchField.getText().toLowerCase();
        String selectedCategory = filterCategoryComboBox.getValue();
        
        ObservableList<Recipe> filteredRecipes = FXCollections.observableArrayList();
        
        for (Recipe recipe : recipes) {
            boolean matchesSearch = searchText.isEmpty() ||
                recipe.getName().toLowerCase().contains(searchText) ||
                recipe.getInstructions().toLowerCase().contains(searchText);
                
            boolean matchesCategory = selectedCategory == null || 
                selectedCategory.equals(recipe.getCategory());
                
            if (matchesSearch && matchesCategory) {
                filteredRecipes.add(recipe);
            }
        }
        
        recipeTable.setItems(filteredRecipes);
        updateRecipeMatches();
    }

    @FXML
    private void handleShowRecipeDetails() {
        Recipe selectedRecipe = recipeTable.getSelectionModel().getSelectedItem();
        if (selectedRecipe != null) {
            StringBuilder details = new StringBuilder();
            details.append("Tarif Adı: ").append(selectedRecipe.getName()).append("\n");
            details.append("Kategori: ").append(selectedRecipe.getCategory()).append("\n");
            details.append("Hazırlama Süresi: ").append(selectedRecipe.getPreparationTime()).append(" dakika\n");
            details.append("Talimatlar:\n").append(selectedRecipe.getInstructions()).append("\n");
            details.append("Malzemeler:\n").append(selectedRecipe.getIngredientsAsString());
            
            recipeDetailArea.setText(details.toString());
        }
    }

    private void loadRecipes() {
        recipes.clear();
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT t.tarif_id, t.tarif_adi, k.kategori_adi, t.hazirlama_suresi, t.talimatlar FROM tarifler t JOIN kategoriler k ON t.kategori_id = k.kategori_id ORDER BY t.tarif_adi";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Recipe recipe = new Recipe(
                        rs.getInt("tarif_id"),
                        rs.getString("tarif_adi"),
                        rs.getString("kategori_adi"),
                        rs.getInt("hazirlama_suresi"),
                        rs.getString("talimatlar")
                    );
                    // Malzemeleri yükle
                    String ingredientSql = "SELECT m.malzeme_adi, tm.malzeme_miktar, m.malzeme_birim FROM tarif_malzemeler tm JOIN malzemeler m ON tm.malzeme_id = m.malzeme_id WHERE tm.tarif_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(ingredientSql)) {
                        pstmt.setInt(1, recipe.getId());
                        try (ResultSet ingredientRs = pstmt.executeQuery()) {
                            while (ingredientRs.next()) {
                                recipe.getIngredients().add(new Recipe.RecipeIngredient(
                                    ingredientRs.getString("malzeme_adi"),
                                    ingredientRs.getDouble("malzeme_miktar"),
                                    ingredientRs.getString("malzeme_birim")
                                ));
                            }
                        }
                    }
                    recipes.add(recipe);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        recipeTable.setItems(recipes);
        updateRecipeMatches();
    }

    private void clearFields() {
        nameField.clear();
        categoryComboBox.setValue(null);
        prepTimeField.clear();
        instructionsArea.clear();
        ingredients.clear();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void loadExistingIngredients() {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT malzeme_adi FROM malzemeler ORDER BY malzeme_adi";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                ObservableList<String> ingredientList = FXCollections.observableArrayList();
                while (rs.next()) {
                    ingredientList.add(rs.getString("malzeme_adi"));
                }
                existingIngredientsComboBox.setItems(ingredientList);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadAvailableIngredients() {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT DISTINCT malzeme_adi FROM malzemeler ORDER BY malzeme_adi";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                availableIngredients.clear();
                while (rs.next()) {
                    availableIngredients.add(rs.getString("malzeme_adi"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddIngredientToSearch() {
        String selected = availableIngredientsList.getSelectionModel().getSelectedItem();
        if (selected != null && !selectedIngredients.contains(selected)) {
            selectedIngredients.add(selected);
            updateRecipeMatches();
        }
    }

    @FXML
    private void handleRemoveIngredientFromSearch() {
        String selected = selectedIngredientsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedIngredients.remove(selected);
            updateRecipeMatches();
        }
    }

    private void updateRecipeMatches() {
        for (Recipe recipe : recipes) {
            double matchPercentage = calculateMatchPercentage(recipe);
            recipeMatchPercentages.put(recipe.getId(), matchPercentage);
        }
        recipeTable.refresh();
    }

    private double calculateMatchPercentage(Recipe recipe) {
        if (selectedIngredients.isEmpty()) {
            return 0.0;
        }

        Set<String> recipeIngredients = new HashSet<>(Arrays.asList(recipe.getIngredientsAsString().split(", ")));
        int matchCount = 0;
        for (String selectedIngredient : selectedIngredients) {
            if (recipeIngredients.contains(selectedIngredient)) {
                matchCount++;
            }
        }
        return (double) matchCount / selectedIngredients.size() * 100.0;
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hakkında");
        alert.setHeaderText("Tarif Rehberi");
        alert.setContentText("Tarif Rehberi v1.0\n\nBu uygulama, tariflerinizi organize etmenize ve malzemelere göre tarif aramanıza yardımcı olur.");
        alert.showAndWait();
    }
} 
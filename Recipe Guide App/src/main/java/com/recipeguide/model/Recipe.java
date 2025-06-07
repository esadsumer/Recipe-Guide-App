package com.recipeguide.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import java.util.List;

public class Recipe {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty category = new SimpleStringProperty();
    private final IntegerProperty preparationTime = new SimpleIntegerProperty();
    private final StringProperty instructions = new SimpleStringProperty();
    private final ListProperty<RecipeIngredient> ingredients = new SimpleListProperty<>();

    public Recipe(int id, String name, String category, int preparationTime, String instructions) {
        setId(id);
        setName(name);
        setCategory(category);
        setPreparationTime(preparationTime);
        setInstructions(instructions);
        setIngredients(FXCollections.observableArrayList());
    }

    // Property getters
    public IntegerProperty idProperty() { return id; }
    public StringProperty nameProperty() { return name; }
    public StringProperty categoryProperty() { return category; }
    public IntegerProperty preparationTimeProperty() { return preparationTime; }
    public StringProperty instructionsProperty() { return instructions; }
    public ListProperty<RecipeIngredient> ingredientsProperty() { return ingredients; }

    // Regular getters and setters
    public int getId() { return id.get(); }
    public void setId(int id) { this.id.set(id); }

    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }

    public String getCategory() { return category.get(); }
    public void setCategory(String category) { this.category.set(category); }

    public int getPreparationTime() { return preparationTime.get(); }
    public void setPreparationTime(int preparationTime) { this.preparationTime.set(preparationTime); }

    public String getInstructions() { return instructions.get(); }
    public void setInstructions(String instructions) { this.instructions.set(instructions); }

    public List<RecipeIngredient> getIngredients() { return ingredients.get(); }
    public void setIngredients(List<RecipeIngredient> ingredients) { this.ingredients.set(FXCollections.observableArrayList(ingredients)); }

    public String getIngredientsAsString() {
        List<RecipeIngredient> ings = getIngredients();
        if (ings == null || ings.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RecipeIngredient ing : ings) {
            if (ing != null && ing.getName() != null) {
                sb.append(ing.getName()).append(" - ")
                  .append(ing.getAmount()).append(" ")
                  .append(ing.getUnit() != null ? ing.getUnit() : "").append(", ");
            }
        }
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    public static class RecipeIngredient {
        private final String name;
        private final double amount;
        private final String unit;

        public RecipeIngredient(String name, double amount, String unit) {
            this.name = name;
            this.amount = amount;
            this.unit = unit;
        }

        public String getName() { return name; }
        public double getAmount() { return amount; }
        public String getUnit() { return unit; }

        @Override
        public String toString() {
            return String.format("%s - %.2f %s", name, amount, unit);
        }
    }
} 
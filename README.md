======================================
AdobongKangkong
A Precision Nutrition + Recipe + Logging System for Android
=====================================

AdobongKangkong is a personal nutrition, recipe, and food logging application
built for precision, correctness, and long-term usability.

It is designed for users who care about:
- Accurate macro + micronutrient tracking
- Recipe-based cooking with real cooked yields
- Per-gram nutrition correctness
- Fast, low-friction food logging
- Long-term health and performance tracking

This is not a calorie counter.
It is a nutrition system.

--------------------------------------------------------
DESIGN PHILOSOPHY
--------------------------------------------------------

1) Data correctness > convenience

   Most nutrition apps prioritize speed at the cost of correctness.
   AdobongKangkong does the opposite:
   - Every logged item stores a nutrition snapshot.
   - All math is domain-correct.
   - Cooked recipe yield is treated as first-class data.

   Once logged, nutrition data is immutable and historically accurate.

2) Per-gram accuracy for cooked recipes

   Cooked food weight is almost never equal to raw ingredient weight.
   Therefore:
   - Recipes have ingredient-level nutrition math.
   - Cooked batches store real-world cooked yield in grams.
   - Logging by grams scales nutrition from the cooked yield.

   This eliminates the biggest source of error in recipe logging.

3) Explicit modeling over hidden heuristics

   Concepts like:
   - servings
   - grams per serving
   - package sizes
   - cooked yields
   - batch history

   are all modeled explicitly in the domain layer instead of being inferred.

4) Architecture first

   The app follows a strict layered architecture:

   UI → ViewModel → UseCase → Domain → Repository → Database

   This ensures:
   - Testability
   - Maintainability
   - Long-term extensibility

5) Designed for long-term tracking

   The system is built for:
   - Multi-year logs
   - Evolving nutrition goals
   - Changing food databases
   - Historical accuracy


--------------------------------------------------------
CORE FEATURES
--------------------------------------------------------

• Food database with:
  - Macros
  - Vitamins
  - Minerals
  - Flexible serving units
  - Per-gram normalization

• Recipe builder:
  - Ingredient-based nutrition computation
  - Cooked yield modeling
  - Servings-based and grams-based logging

• Cooked batch system:
  - Stores real cooked yield weight
  - Enables per-gram logging for recipes
  - Keeps nutrition math correct even after cooking

• High-precision logging:
  - Every log entry stores a nutrition snapshot
  - Historical logs remain correct even if food data changes later

• Smart UX features:
  - Package shortcuts (½ package, 1 package)
  - Pound → gram conversion
  - String-backed numeric inputs (no cursor jumps)
  - Quick add logging

• Food metadata flags:
  - Favorite
  - Eat more
  - Limit this

--------------------------------------------------------
SYSTEM ARCHITECTURE OVERVIEW
--------------------------------------------------------

Domain modeling is the foundation.

Key domain concepts:

Food
  - A single nutritional entity.
  - May represent:
      • Raw ingredient
      • Packaged food
      • Recipe proxy

Recipe
  - A composition of Food + servings
  - Computes macro + micronutrients mathematically

RecipeBatch
  - Represents one real cooking event
  - Stores final cooked yield in grams
  - Enables cooked-gram logging

LogEntry
  - Immutable nutrition snapshot
  - Stores resolved nutrient values at log time
  - Guarantees historical correctness

NutritionSnapshot
  - Canonical normalized nutrition representation
  - Per-serving + per-gram values


--------------------------------------------------------
USER MANUAL (BASIC WORKFLOW)
--------------------------------------------------------

--------------------------------------------------------
1) ADDING FOODS
--------------------------------------------------------

1. Open Food Editor
2. Enter:
   - Name
   - Brand (optional)
   - Serving size + unit
   - Grams per serving (optional but recommended)
   - Servings per package (optional)

3. Optional:
   - Mark as Favorite
   - Mark as Eat More
   - Mark as Limit

4. Save

TIP:
You can long-tap / press the "lb" button next to grams input
to enter pounds and auto-convert to grams.

--------------------------------------------------------
2) BUILDING RECIPES
--------------------------------------------------------

1. Open Recipe Builder
2. Enter recipe name
3. Add ingredients:
   - Select food
   - Enter servings
   - Optionally use:
       • ½ package
       • 1 package

4. Set:
   - Servings yield
   - (Optional) expected cooked yield grams

5. Save recipe

Recipes behave as foods and can be logged like normal foods.

--------------------------------------------------------
3) COOKING & CREATING A BATCH
--------------------------------------------------------

When you cook a recipe:

1. Weigh the final cooked product
2. Open Quick Add
3. Select the recipe
4. Tap "Create batch"
5. Enter cooked yield in grams
6. Save batch

This batch now represents *this specific cooking event*.

--------------------------------------------------------
4) LOGGING FOOD
--------------------------------------------------------

A) Logging regular foods:

1. Open Quick Add
2. Select food
3. Enter:
   - Servings
   - Or grams (if available)
4. Tap Log

B) Logging cooked recipes by grams:

1. Open Quick Add
2. Select recipe
3. Choose cooked batch
4. Enter grams eaten
5. Tap Log

The system scales nutrition by:

    grams eaten / cooked batch yield

This ensures accurate macro and micronutrient logging.

--------------------------------------------------------
WHY THIS SYSTEM IS DIFFERENT
--------------------------------------------------------

Most nutrition apps:

  - Guess cooked yield
  - Ignore water loss/gain
  - Log based on inaccurate serving assumptions
  - Rewrite nutrition history when foods change

AdobongKangkong:

  - Stores real cooked yield
  - Uses per-gram nutrition math
  - Freezes nutrition snapshots at log time
  - Preserves historical accuracy forever

--------------------------------------------------------
PROJECT STATUS
--------------------------------------------------------

This project is under active development.

Primary focus:
  - Logging accuracy
  - Domain correctness
  - UX speed

Future goals:
  - Nutrient targets & limits
  - Dashboard & analytics
  - Long-term trend tracking
  - Smart suggestions

--------------------------------------------------------
LICENSE
--------------------------------------------------------

Personal research and development project.
Not currently licensed for commercial use.

--------------------------------------------------------
END
--------------------------------------------------------

using Generated.Model.Foods;
using Generated.Model.Foods.Recipe;
using MobileClient.RecipeExample.SimpleSync;
using MobileSyncModels.Base;
using MobileSyncModels.Services;
using SQLiteNetExtensions.Extensions;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using Xamarin.Forms;

namespace MobileClient.RecipeExample
{
    public class RecipeEnvelop : NotifyPropertyChanged
    {
        public Recipe Recipe { get; set; }
        public string DisplayRecipe { get { return Recipe?.User?.Name + " - " + Recipe?.Name; } }

        private ObservableCollection<IngredientEnvelop> ingredients;
        public ObservableCollection<IngredientEnvelop> Ingredients
        {
            get
            {
                return ingredients ?? (ingredients =
                                            new ObservableCollection<IngredientEnvelop>(Recipe
                                                                                            .Ingredients
                                                                                                .Select(i => new IngredientEnvelop
                                                                                                {
                                                                                                    Ingredient = i
                                                                                                })
                                                                                                .OrderBy(ie => ie.Ingredient.Food.Name)));
            }
        }
    }

    public class IngredientEnvelop : NotifyPropertyChanged
    {
        public Ingredient Ingredient { get; set; }
        public string DisplayIngredient { get { return Ingredient?.Amount.ToString("0.0") + " g of " + Ingredient?.Food?.Name; } }
    }

    public class RecipeViewModel : BaseViewModel
    {
        public Command CommandInsertRecipe { get; set; }
        public Command CommandUpdateRecipe { get; set; }
        public Command CommandDeleteRecipe { get; set; }

        private ObservableCollection<RecipeEnvelop> recipes;
        public ObservableCollection<RecipeEnvelop> Recipes
        {
            get
            {
                return recipes ??
                    (recipes = new ObservableCollection<RecipeEnvelop>(
                                        Get<IDatabaseConnection>().Connection.GetAllWithChildren<Recipe>(null, true)
                                                        .Select(r => new RecipeEnvelop { Recipe = r })
                                                        .OrderBy(re => re.DisplayRecipe)));
            }
        }

        private RecipeEnvelop selectedRecipe;
        public RecipeEnvelop SelectedRecipe
        {
            get
            {
                return selectedRecipe;
            }
            set
            {
                selectedRecipe = value;
                RaisePropertyChanged(nameof(SelectedRecipe));
                RaisePropertyChanged(nameof(Energy));
                RefreshCommands();
            }
        }

        private string recipeName;
        public string RecipeName
        {
            get { return recipeName; }
            set { recipeName = value; RefreshCommands(); }
        }

        public string Energy
        {
            get
            {
                return "Energy: " + SelectedRecipe?.Ingredients.Sum(i => i.Ingredient.Amount / 100 * i.Ingredient.Food.Energy).ToString("0.0") + " kJ";
            }
        }

        public RecipeViewModel()
        {
            Get<INotificationService>().Subscribe(NotificationEvent.Synchronized, RefreshRecipes);
            Get<INotificationService>().Subscribe(NotificationEvent.SynchronizationFailed, RefreshRecipes);
            Get<INotificationService>().Subscribe(NotificationEvent.Reset, () => { SelectedRecipe = null; RefreshRecipes(); });

            CommandInsertRecipe = new Command(
                InsertRecipe,
                () => Get<IBaseModelService>().User != null && !string.IsNullOrWhiteSpace(RecipeName));
            CommandUpdateRecipe = new Command(
                UpdateRecipe,
                () => SelectedRecipe?.Recipe.User?.Pk == Get<IBaseModelService>().User?.Pk);
            CommandDeleteRecipe = new Command(
                DeleteRecipe,
                () => SelectedRecipe?.Recipe.User?.Pk == Get<IBaseModelService>().User?.Pk);
        }

        private void DeleteRecipe()
        {
            DoAndKeepSelection(() =>
            {
                foreach (Ingredient ingredient in SelectedRecipe.Recipe.Ingredients)
                {
                    Delete(ingredient);
                }

                Delete(SelectedRecipe.Recipe);
                Recipes.Remove(SelectedRecipe);
            });
            RefreshCommands();
        }

        private void InsertRecipe()
        {
            Recipe recipe = new Recipe
            {
                User = Get<IBaseModelService>().User,
                UserFk = Get<IBaseModelService>().User.Pk,
                Name = RecipeName,
                IsNew = true,
            };

            try
            {
                Upsert(recipe);

                List<Food> allFoods = Get<IDatabaseConnection>().Connection.Query<Food>("select * from Food");

                foreach (Food food in allFoods
                                        .Select(f => new
                                        {
                                            Food = f,
                                            Random = (int)(random() * 100)
                                        })
                                        .Where(o => o.Random % 2 == 1)
                                        .Select(o => o.Food))
                {
                    Upsert(new Ingredient
                    {
                        IsNew = true,
                        RecipeFk = recipe.Pk,
                        FoodFk = food.Pk,
                        Amount = 25d + random() * 75
                    });
                }

                Get<IDatabaseConnection>().Connection.GetChildren(recipe, true);

                RecipeEnvelop recipeEnvelop = new RecipeEnvelop
                {
                    Recipe = recipe
                };

                Recipes.Insert(SelectedRecipe == null ? 0 : Recipes.IndexOf(SelectedRecipe), recipeEnvelop);
                SelectedRecipe = recipeEnvelop;
            }
            catch (Exception exception)
            {
                ShowException("Insert", exception);
            }
        }

        private void UpdateRecipe()
        {
            try
            {
                if (!string.IsNullOrWhiteSpace(RecipeName))
                {
                    SelectedRecipe.Recipe.Name = RecipeName;
                    Upsert(SelectedRecipe.Recipe);
                }

                if (SelectedRecipe.Recipe.Ingredients.GroupBy(i => i.Amount).Count() > 1)
                {
                    foreach (Ingredient ingredient in SelectedRecipe.Recipe.Ingredients)
                    {
                        ingredient.Amount = 50;
                        Upsert(ingredient);
                    }
                }
                else
                {
                    foreach (Ingredient ingredient in SelectedRecipe.Recipe.Ingredients)
                    {
                        ingredient.Amount = 25d + random() * 75;
                        Upsert(ingredient);
                    }
                }

                RefreshRecipes();
            }
            catch (Exception exception)
            {
                ShowException("Update", exception);
            }
        }

        private void ShowException(string title, Exception exception)
        {
            Application.Current.MainPage.DisplayAlert(
                            title,
                            "Problem" + Environment.NewLine + Environment.NewLine + exception.Message + Environment.NewLine,
                            "Ok");
        }

        private Random Random { get; } = new Random();

        private double random()
        {
            return Random.NextDouble();
        }

        private void RefreshRecipes()
        {
            DoAndKeepSelection(() =>
            {
                recipes = null;
                RaisePropertyChanged(nameof(Recipes));
            });
            RefreshCommands();
        }

        private void DoAndKeepSelection(Action action)
        {
            int selected = Math.Max(Recipes.IndexOf(SelectedRecipe), 0);

            action();

            if (Recipes.Count > 0)
            {
                if (selected < Recipes.Count)
                {
                    SelectedRecipe = Recipes[selected];
                }
                else
                {
                    SelectedRecipe = Recipes.Last();
                }
            }
        }

        private void RefreshCommands()
        {
            foreach (Command command in new[]
            {
                CommandInsertRecipe, CommandUpdateRecipe, CommandDeleteRecipe
            })
            {
                command.ChangeCanExecute();
            }
        }
    }
}

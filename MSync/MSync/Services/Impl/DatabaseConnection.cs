using MobileClient.RecipeExample;
using MobileSyncModels.Services;
using SQLite;
using Xamarin.Forms;

[assembly: Xamarin.Forms.Dependency(typeof(DatabaseConnection))]
namespace MobileClient.RecipeExample
{
    public class DatabaseConnection : IDatabaseConnection
    {
        public DatabaseConnection()
        {
            IBaseModelService baseModelService = DependencyService.Get<IBaseModelService>();
        }
        public SQLiteConnection Connection { get; set; }

        public string Version { get; set; }
    }
}

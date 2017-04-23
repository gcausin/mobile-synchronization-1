using MobileSync.Example.Generic;
using MobileSyncModels.Services;
using SQLite;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

using Xamarin.Forms;

namespace MobileSync.Example
{
    public partial class App : Application
    {
        public App()
        {
            ISynchronizationService synchronizationService = DependencyService.Get<ISynchronizationService>();
            IDatabaseConnection databaseConnection = DependencyService.Get<IDatabaseConnection>();

            string path = DependencyService.Get<IFileHelper>().GetLocalFilePath("recipe-example.db3");

            databaseConnection.Connection = new SQLiteConnection(path);
            databaseConnection.Version = GlobalConstants.Version;

            synchronizationService.CreateTables();

            InitializeComponent();

            MainPage = new MobileClient.RecipeExample.MainPage();
        }

        protected override void OnStart()
        {
            // Handle when your app starts
        }

        protected override void OnSleep()
        {
            // Handle when your app sleeps
        }

        protected override void OnResume()
        {
            // Handle when your app resumes
        }
    }
}

using System;
using System.IO;
using Xamarin.Forms;
using MobileSync.Example.Droid.SimpleSync;
using MobileSync;

[assembly: Dependency(typeof(FileHelper))]
namespace MobileSync.Example.Droid.SimpleSync
{
    public class FileHelper : IFileHelper
    {
        public string GetLocalFilePath(string filename)
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Personal), filename);
        }
    }
}
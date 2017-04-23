using MobileSyncModels.Services;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Xamarin.Forms;

[assembly: Xamarin.Forms.Dependency(typeof(ServiceProviderService))]
namespace MobileSyncModels.Services
{
    public class ServiceProviderService : IServiceProviderService
    {
        private Dictionary<Type, object> Services { get; } = new Dictionary<Type, object>();

        public T Get<T>() where T: class
        {
            object found;

            if (Services.TryGetValue(typeof(T), out found))
            {
                return found as T;
            }

            found = DependencyService.Get<T>();

            Services.Add(typeof(T), found);

            return found as T;
        }
    }
}

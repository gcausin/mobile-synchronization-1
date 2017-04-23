using MobileSyncModels.Services;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Xamarin.Forms;

namespace MobileSyncModels.Base
{
    public class ServiceProvider
    {
        private IServiceProviderService provider;
        public IServiceProviderService Provider
        {
            get { return provider ?? (provider = DependencyService.Get<IServiceProviderService>()); }
        }

        public T Get<T>() where T : class
        {
            return Provider.Get<T>();
        }
    }
}

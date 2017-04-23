using Generated.MobileSynchronization;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace MobileSync.Example.Generic
{
    public static class GlobalConstants
    {
        public static readonly string ClientVersion = "2";
        public static readonly string Version = GeneratedConstants.ServerVersion + "." + ClientVersion;
        public static readonly string Server = GeneratedConstants.Server;
    }
}
